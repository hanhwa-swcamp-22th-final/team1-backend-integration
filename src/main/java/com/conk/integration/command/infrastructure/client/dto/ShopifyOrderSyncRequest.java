package com.conk.integration.command.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// order-service POST /orders/seller/shopify 호출용 요청 DTO — CreateOrderRequest와 동일 구조로 미러링한다.
@Getter
@AllArgsConstructor
public class ShopifyOrderSyncRequest {

    private LocalDateTime orderedAt;
    private String channelOrderNo;
    private String receiverName;
    private String receiverPhoneNo;
    private String memo;
    private ShippingAddress shippingAddress;
    private List<OrderItem> items;

    @Getter
    @AllArgsConstructor
    public static class ShippingAddress {
        private String address1;
        private String address2;
        private String city;
        private String state;
        private String zipCode;
    }

    @Getter
    @AllArgsConstructor
    public static class OrderItem {
        private String sku;
        private int quantity;
        private String productNameSnapshot;
    }
}
