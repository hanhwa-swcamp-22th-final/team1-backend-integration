package com.conk.integration.command.application.service.shopify;

import com.conk.integration.command.infrastructure.service.shopify.ShopifyFulfillmentRequest;
import com.conk.integration.command.application.service.ChannelFulfillmentSender;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.application.dto.FulfillmentTargetDto;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyFulfillmentApiClient;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyOrderClient;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.query.service.ChannelApiQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

// Shopify 채널 주문을 fulfillment API 형식으로 변환해 전송한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyFulfillmentSender implements ChannelFulfillmentSender {

    private final ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;
    private final ShopifyOrderClient shopifyOrderClient;
    private final ChannelApiQueryService channelApiQueryService;

    @Override
    public boolean supports(OrderChannel channel) {
        return channel == OrderChannel.SHOPIFY;
    }

    @Override
    public void send(ChannelOrder order, EasypostShipmentInvoice invoice) {
        ChannelCredential credential = channelApiQueryService.findChannelCredential(order.getSellerId(), OrderChannel.SHOPIFY.name());

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
                credential.getStoreName(), credential.getChannelApi(), order.getChannelOrderNo(), request);
    }

    @Override
    public void sendBulk(String sellerId, List<FulfillmentTargetDto> targets) {
        ChannelCredential credential = channelApiQueryService.findChannelCredential(sellerId, OrderChannel.SHOPIFY.name());
        resolveFulfillmentOrderIds(credential, targets);
        shopifyFulfillmentApiClient.createBulkFulfillment(
                credential.getStoreName(),
                credential.getChannelApi(),
                targets);
    }

    // fulfillmentOrderId가 없는 주문은 Shopify에서 온디맨드로 조회한다.
    // read_merchant_managed_fulfillment_orders scope 필요.
    private void resolveFulfillmentOrderIds(ChannelCredential credential, List<FulfillmentTargetDto> targets) {
        for (FulfillmentTargetDto target : targets) {
            if (target.getFulfillmentOrderId() == null) {
                String fid = shopifyOrderClient.getFulfillmentOrderId(
                        credential.getStoreName(), credential.getChannelApi(), target.getChannelOrderNo());
                if (fid == null) {
                    log.warn("fulfillmentOrderId 조회 실패 — channelOrderNo={}", target.getChannelOrderNo());
                }
                target.setFulfillmentOrderId(fid);
            }
        }
    }
}
