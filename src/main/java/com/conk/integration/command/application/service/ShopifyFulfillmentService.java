package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.domain.aggregate.CarrierType;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.service.ShopifyFulfillmentApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 저장된 주문/송장 정보를 조합해 Shopify 출고 API를 호출한다.
@Service
@RequiredArgsConstructor
public class ShopifyFulfillmentService {

    private final ChannelOrderRepository channelOrderRepository;
    private final EasypostShipmentInvoiceRepository invoiceRepository;
    private final ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;

    /**
     * Shopify 주문 출고 확인
     * EasyPost 송장의 trackingNumber를 Shopify에 전달해 주문 상태를 fulfilled로 업데이트
     */
    public void fulfill(String orderId) {
        ChannelOrder order = channelOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("ChannelOrder를 찾을 수 없습니다: " + orderId));

        if (order.getInvoiceNo() == null) {
            throw new IllegalStateException("송장이 발급되지 않은 주문입니다: " + orderId);
        }

        EasypostShipmentInvoice invoice = invoiceRepository.findById(order.getInvoiceNo())
                .orElseThrow(() -> new IllegalStateException("EasypostShipmentInvoice를 찾을 수 없습니다: " + order.getInvoiceNo()));

        ShopifyFulfillmentRequest request = ShopifyFulfillmentRequest.builder()
                .fulfillment(ShopifyFulfillmentRequest.FulfillmentBody.builder()
                        // Shopify가 기대하는 추적 정보 형식으로 송장 데이터를 변환한다.
                        .trackingInfo(ShopifyFulfillmentRequest.TrackingInfo.builder()
                                .number(invoice.getInvoiceNo())
                                .company(resolveCarrierCompany(invoice.getCarrierType()))
                                .build())
                        .notifyCustomer(true)
                        .build())
                .build();

        shopifyFulfillmentApiClient.createFulfillment(order.getChannelOrderNo(), request);
    }

    // 내부 운송사 enum을 Shopify가 읽는 문자열로 변환한다.
    String resolveCarrierCompany(CarrierType carrierType) {
        if (carrierType == null) return "USPS";
        return switch (carrierType) {
            case UPS -> "UPS";
            case FEDEX -> "FedEx";
            default -> "USPS";
        };
    }
}
