package com.conk.integration.command.application.service.shopify;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.application.service.ChannelFulfillmentSender;
import com.conk.integration.command.domain.aggregate.CarrierType;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.command.infrastructure.service.ShopifyFulfillmentApiClient;
import com.conk.integration.query.dto.FulfillmentTargetDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Shopify 채널 주문을 fulfillment API 형식으로 변환해 전송한다.
@Service
@RequiredArgsConstructor
public class ShopifyFulfillmentSender implements ChannelFulfillmentSender {

    private final ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;

    @Override
    public boolean supports(OrderChannel channel) {
        return channel == OrderChannel.SHOPIFY;
    }

    @Override
    public void send(ChannelOrder order, EasypostShipmentInvoice invoice) {
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

    @Override
    public void sendBulk(List<FulfillmentTargetDto> targets) {
        shopifyFulfillmentApiClient.createBulkFulfillment(targets);
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
