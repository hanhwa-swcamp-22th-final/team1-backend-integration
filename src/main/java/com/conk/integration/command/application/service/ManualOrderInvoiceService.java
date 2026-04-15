package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.request.ManualOrderInvoiceRequest;
import com.conk.integration.command.infrastructure.config.EasyPostProperties;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostShipmentResponse;
import com.conk.integration.command.application.dto.response.ManualOrderInvoiceResponse;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostApiClient;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostShipmentConverter;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 수동 주문 기입 및 EasyPost 송장 발급 전체 흐름을 조율한다.
@Service
@RequiredArgsConstructor
public class ManualOrderInvoiceService {

    private final ChannelOrderRepository channelOrderRepository;
    private final EasyPostApiClient easyPostApiClient;
    private final InvoicePersistenceService invoicePersistenceService;
    private final EasyPostProperties easyPostProperties;

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
        EasyPostShipmentResponse.RateDto cheapest = EasyPostShipmentConverter.selectCheapestRate(shipment.getRates());
        EasyPostShipmentResponse bought = easyPostApiClient.buyRate(shipment.getId(), cheapest.getId());

        // ⑤ [TX-3, REQUIRES_NEW] invoice 저장 + invoiceNo 업데이트
        EasypostShipmentInvoice invoice = EasyPostShipmentConverter.toInvoice(bought, null, null, easyPostProperties.getTrackingUrlPrefix());
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
                        throw new BusinessException(ErrorCode.INVOICE_ALREADY_EXISTS, "이미 송장이 발급된 주문입니다: " + request.getOrderId());
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
}
