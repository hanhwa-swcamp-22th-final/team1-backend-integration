package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.request.ManualOrderInvoiceRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.application.dto.response.ManualOrderInvoiceResponse;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.EasyPostApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 수동 주문 기입 및 EasyPost 송장 발급 전체 흐름을 조율한다.
@Service
@RequiredArgsConstructor
public class ManualOrderInvoiceService {

    private final ChannelOrderRepository channelOrderRepository;
    private final EasyPostApiClient easyPostApiClient;
    private final InvoicePersistenceService invoicePersistenceService;

    /**
     * 수동 주문을 저장하고 EasyPost 송장을 발급한다.
     *
     * 흐름:
     * 재시도 체크 → ①[TX-1] 주문 저장 → ② createShipment → ③[TX-2] shipmentId 기록
     * → ④ buyRate → ⑤[TX-3, REQUIRES_NEW] invoice 저장 + invoiceNo 업데이트
     *
     * TX-1/TX-2는 JpaRepository 자체 트랜잭션으로 각각 즉시 커밋된다.
     * TX-3는 InvoicePersistenceService(별도 Bean)의 REQUIRES_NEW로 커밋된다.
     */
    public ManualOrderInvoiceResponse issue(String sellerId, ManualOrderInvoiceRequest request) {
        ChannelOrder order = resolveOrder(sellerId, request);

        // ② EasyPost shipment 생성
        EasyPostCreateShipmentRequest shipmentRequest = buildShipmentRequest(request);
        EasyPostShipmentResponse shipment = easyPostApiClient.createShipment(shipmentRequest);

        // ③ [TX-2] shipmentId 기록 — buyRate crash 시 추적 가능하도록 미리 저장
        order.assignShipmentId(shipment.getId());
        channelOrderRepository.save(order);

        // ④ EasyPost rate 선택 및 구매 (실제 결제 — 이후 롤백 불가)
        EasyPostShipmentResponse.RateDto cheapest = selectCheapestRate(shipment.getRates());
        EasyPostShipmentResponse bought = easyPostApiClient.buyRate(shipment.getId(), cheapest.getId());

        // ⑤ [TX-3, REQUIRES_NEW] invoice 저장 + invoiceNo 업데이트
        EasypostShipmentInvoice invoice = toInvoice(bought);
        EasypostShipmentInvoice saved = invoicePersistenceService.saveInvoiceAndAssign(invoice, order);

        return ManualOrderInvoiceResponse.of(order, saved);
    }

    // orderId가 없으면 ①[TX-1] 새 주문을 저장한다.
    // 이미 존재하고 invoiceNo=null 이면 재시도로 간주해 기존 주문을 반환한다.
    // 이미 송장이 발급된 주문이면 예외를 던진다.
    private ChannelOrder resolveOrder(String sellerId, ManualOrderInvoiceRequest request) {
        return channelOrderRepository.findById(request.getOrderId())
                .map(existing -> {
                    if (existing.getInvoiceNo() != null) {
                        throw new IllegalStateException("이미 송장이 발급된 주문입니다: " + request.getOrderId());
                    }
                    return existing;
                })
                .orElseGet(() -> saveNewOrder(sellerId, request));
    }

    // ① [TX-1] 새 ChannelOrder + ChannelOrderItem 저장.
    // JpaRepository.save() 자체 @Transactional로 즉시 커밋된다.
    private ChannelOrder saveNewOrder(String sellerId, ManualOrderInvoiceRequest request) {
        ChannelOrder order = ChannelOrder.builder()
                .orderId(request.getOrderId())
                .orderChannel(OrderChannel.MANUAL)
                .orderedAt(LocalDateTime.now())
                .sellerId(sellerId)
                .receiverName(request.getReceiverName())
                .receiverPhoneNo(request.getReceiverPhoneNo())
                .shipToAddress1(request.getShipToAddress1())
                .shipToAddress2(request.getShipToAddress2())
                .shipToState(request.getShipToState())
                .shipToCity(request.getShipToCity())
                .shipToZipCode(request.getShipToZipCode())
                .build();

        if (request.getItems() != null) {
            request.getItems().forEach(item -> {
                ChannelOrderItem orderItem = ChannelOrderItem.builder()
                        .id(new ChannelOrderItemId(order.getOrderId(), item.getSkuId()))
                        .channelOrder(order)
                        .productNameSnapshot(item.getProductNameSnapshot())
                        .quantity(item.getQuantity())
                        .build();
                order.addItem(orderItem);
            });
        }

        return channelOrderRepository.save(order);
    }

    private EasyPostCreateShipmentRequest buildShipmentRequest(ManualOrderInvoiceRequest request) {
        EasyPostCreateShipmentRequest.AddressBody toAddress =
                EasyPostCreateShipmentRequest.AddressBody.builder()
                        .name(request.getReceiverName())
                        .phone(request.getReceiverPhoneNo())
                        .street1(request.getShipToAddress1())
                        .street2(request.getShipToAddress2())
                        .city(request.getShipToCity())
                        .state(request.getShipToState())
                        .zip(request.getShipToZipCode())
                        .country("US")
                        .build();

        return EasyPostCreateShipmentRequest.builder()
                .shipment(EasyPostCreateShipmentRequest.ShipmentBody.builder()
                        .toAddress(toAddress)
                        .fromAddress(request.getFromAddress())
                        .parcel(request.getParcel())
                        .build())
                .build();
    }

    private EasyPostShipmentResponse.RateDto selectCheapestRate(List<EasyPostShipmentResponse.RateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            throw new IllegalStateException("운임 정보가 없습니다");
        }
        return rates.stream()
                .filter(r -> r.getRate() != null && isNumeric(r.getRate()))
                .min(Comparator.comparingDouble(r -> Double.parseDouble(r.getRate())))
                .orElseThrow(() -> new IllegalStateException("유효한 운임 정보가 없습니다"));
    }

    private EasypostShipmentInvoice toInvoice(EasyPostShipmentResponse response) {
        EasyPostShipmentResponse.RateDto selected = response.getSelectedRate();
        String labelUrl = response.getPostageLabel() != null ? response.getPostageLabel().getLabelUrl() : null;
        String trackingUrl = resolveTrackingUrl(response);
        String shipToAddress = resolveShipToAddress(response.getToAddress());

        int freightChargeAmtCents = 0;
        if (selected != null && selected.getRate() != null && isNumeric(selected.getRate())) {
            freightChargeAmtCents = (int) Math.round(Double.parseDouble(selected.getRate()) * 100);
        }

        CarrierType carrierType = selected != null
                ? CarrierType.fromEasyPostName(selected.getCarrier())
                : CarrierType.USPS;

        return EasypostShipmentInvoice.builder()
                .invoiceNo(response.getId())
                .trackingCode(response.getTrackingCode())
                .carrierType(carrierType)
                .freightChargeAmt(freightChargeAmtCents)
                .shipToAddress(shipToAddress)
                .trackingUrl(trackingUrl)
                .labelFileUrl(labelUrl)
                .build();
    }

    private String resolveTrackingUrl(EasyPostShipmentResponse response) {
        if (response.getTracker() != null && response.getTracker().getPublicUrl() != null) {
            return response.getTracker().getPublicUrl();
        }
        if (response.getTrackingCode() != null) {
            return "https://track.easypost.com/" + response.getTrackingCode();
        }
        return null;
    }

    private String resolveShipToAddress(EasyPostShipmentResponse.AddressDto addr) {
        if (addr == null) return null;
        return Stream.of(addr.getStreet1(), addr.getCity(), addr.getState(), addr.getZip(), addr.getCountry())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    private boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
