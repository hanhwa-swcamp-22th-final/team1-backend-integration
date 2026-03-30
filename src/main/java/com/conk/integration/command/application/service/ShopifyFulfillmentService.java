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
                        .trackingInfo(ShopifyFulfillmentRequest.TrackingInfo.builder()
                                .number(invoice.getInvoiceNo())
                                .company(resolveCarrierCompany(invoice.getCarrierType()))
                                .build())
                        .notifyCustomer(true)
                        .build())
                .build();

        shopifyFulfillmentApiClient.createFulfillment(order.getChannelOrderNo(), request);
    }

    String resolveCarrierCompany(CarrierType carrierType) {
        if (carrierType == null) return "USPS";
        return switch (carrierType) {
            case UPS -> "UPS";
            case FEDEX -> "FedEx";
            default -> "USPS";
        };
    }
}
