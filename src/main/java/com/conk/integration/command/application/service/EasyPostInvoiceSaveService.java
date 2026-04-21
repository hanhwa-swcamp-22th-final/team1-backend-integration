package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.request.OrderInvoicePair;
import com.conk.integration.command.application.dto.response.BulkInvoiceResponse;
import com.conk.integration.command.infrastructure.config.EasyPostProperties;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostShipmentResponse;
import com.conk.integration.command.application.dto.InvoiceTargetDto;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.infrastructure.mapper.ChannelOrderInvoiceMapper;
import com.conk.integration.command.infrastructure.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostApiClient;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostShipmentConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// EasyPost shipment 생성 결과를 CONK 송장 엔티티로 변환해 저장한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class EasyPostInvoiceSaveService {

    private final EasyPostApiClient easyPostApiClient;
    private final EasypostShipmentInvoiceRepository invoiceRepository;
    private final ChannelOrderInvoiceMapper channelOrderInvoiceMapper;
    private final EasyPostProperties easyPostProperties;
    private final ObjectMapper objectMapper;

    /**
     * 배송 송장을 생성하고 DB에 저장한다.
     * 1) shipment 생성 → 2) 최저가 rate 선택 → 3) rate 구매 → 4) DB 저장
     *
     * @param request EasyPost shipment 생성 요청
     * @return 저장된 송장 엔티티
     */
    public EasypostShipmentInvoice createAndSaveInvoice(EasyPostCreateShipmentRequest request) {
        EasyPostShipmentResponse shipment = easyPostApiClient.createShipment(request);

        // EasyPost rate 목록 중 가장 저렴한 운임만 구매 대상으로 선택한다.
        try {
            log.info("rates: {}", objectMapper.writeValueAsString(shipment.getRates()));
        } catch (JsonProcessingException e) {
            log.warn("rates 직렬화 실패", e);
        }
        EasyPostShipmentResponse.RateDto cheapest = EasyPostShipmentConverter.selectCheapestRate(shipment.getRates());

        log.info("shipmentId: {}, rateId: {}", shipment.getId(), cheapest.getId());
        EasyPostShipmentResponse bought = easyPostApiClient.buyRate(shipment.getId(), cheapest.getId());

        EasypostShipmentInvoice invoice = EasyPostShipmentConverter.toInvoice(bought, null, null, easyPostProperties.getTrackingUrlPrefix());
        return invoiceRepository.save(invoice);
    }

    /**
     * invoiceNo가 없는 주문 전체에 대해 송장을 일괄 발급하고 DB에 반영한다.
     * 개별 실패는 failCount로 집계하고 나머지 주문은 계속 처리한다.
     *
     * @param sellerId    대상 셀러
     * @param fromAddress 발송 주소 (공통 적용)
     * @param parcel      소포 정보 (공통 적용)
     * @return 성공/실패 건수 요약
     */
    @Transactional
    public BulkInvoiceResponse createAndSaveBulkInvoices(
            String sellerId,
            EasyPostCreateShipmentRequest.AddressBody fromAddress,
            EasyPostCreateShipmentRequest.ParcelBody parcel) {

        List<InvoiceTargetDto> targets = channelOrderInvoiceMapper.findOrdersWithoutInvoice(sellerId);
        if (targets.isEmpty()) {
            return new BulkInvoiceResponse(0, 0);
        }

        int successCount = 0, failCount = 0;
        List<OrderInvoicePair> successPairs = new ArrayList<>();
        for (InvoiceTargetDto target : targets) {
            try {
                EasyPostCreateShipmentRequest request = buildRequestFromTarget(target, fromAddress, parcel);
                EasypostShipmentInvoice invoice = createAndSaveInvoice(request);
                successPairs.add(new OrderInvoicePair(target.getOrderId(), invoice.getInvoiceNo()));
                successCount++;
            } catch (Exception e) {
                failCount++;
            }
        }
        if (!successPairs.isEmpty()) {
            channelOrderInvoiceMapper.bulkAssignInvoice(successPairs);
        }
        return new BulkInvoiceResponse(successCount, failCount);
    }

    // InvoiceTargetDto 주소 + 공통 fromAddress/parcel 로 EasyPost 요청을 조립한다.
    // ChannelOrder에 country 컬럼이 없으므로 "US"로 고정한다.
    private EasyPostCreateShipmentRequest buildRequestFromTarget(
            InvoiceTargetDto target,
            EasyPostCreateShipmentRequest.AddressBody fromAddress,
            EasyPostCreateShipmentRequest.ParcelBody parcel) {

        EasyPostCreateShipmentRequest.AddressBody toAddress =
                EasyPostCreateShipmentRequest.AddressBody.builder()
                        .name(target.getReceiverName())
                        .phone(target.getReceiverPhoneNo())
                        .street1(target.getShipToAddress1())
                        .city(target.getShipToCity())
                        .state(target.getShipToState())
                        .zip(target.getShipToZipCode())
                        .country("US")
                        .build();

        return EasyPostCreateShipmentRequest.builder()
                .shipment(EasyPostCreateShipmentRequest.ShipmentBody.builder()
                        .toAddress(toAddress)
                        .fromAddress(fromAddress)
                        .parcel(parcel)
                        .build())
                .build();
    }
}
