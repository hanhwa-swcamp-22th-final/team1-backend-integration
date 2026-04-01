package com.conk.integration.command.application.service.shopify;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.application.service.ChannelFulfillmentSender;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.service.ShopifyFulfillmentApiClient;
import com.conk.integration.query.dto.FulfillmentTargetDto;
import com.conk.integration.query.dto.ShopifyCredentialDto;
import com.conk.integration.query.service.ChannelApiQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Shopify 채널 주문을 fulfillment API 형식으로 변환해 전송한다.
@Service
@RequiredArgsConstructor
public class ShopifyFulfillmentSender implements ChannelFulfillmentSender {

    private final ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;
    private final ChannelApiQueryService channelApiQueryService;

    @Override
    public boolean supports(OrderChannel channel) {
        return channel == OrderChannel.SHOPIFY;
    }

    @Override
    public void send(ChannelOrder order, EasypostShipmentInvoice invoice) {
        ShopifyCredentialDto cred = channelApiQueryService.findShopifyCredential(order.getSellerId());

        ShopifyFulfillmentRequest request = ShopifyFulfillmentRequest.builder()
                .fulfillment(ShopifyFulfillmentRequest.FulfillmentBody.builder()
                        // Shopify가 기대하는 추적 정보 형식으로 송장 데이터를 변환한다.
                        .trackingInfo(ShopifyFulfillmentRequest.TrackingInfo.builder()
                                .number(invoice.getInvoiceNo())
                                .company(invoice.getCarrierType().toShopifyName())
                                .build())
                        .notifyCustomer(true)
                        .build())
                .build();

        shopifyFulfillmentApiClient.createFulfillment(
                cred.getStoreName(), cred.getAccessToken(), order.getChannelOrderNo(), request);
    }

    @Override
    public void sendBulk(String sellerId, List<FulfillmentTargetDto> targets) {
        ShopifyCredentialDto cred = channelApiQueryService.findShopifyCredential(sellerId);
        shopifyFulfillmentApiClient.createBulkFulfillment(cred.getStoreName(), cred.getAccessToken(), targets);
    }
}
