package com.conk.integration.command.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

// Shopify 주문 목록 응답에서 orders 배열만 안전하게 꺼내기 위한 래퍼다.
@Setter
@NoArgsConstructor
public class ShopifyOrderListResponse {

    @JsonProperty("orders")
    private List<ShopifyOrderDto> orders;

    // null 대신 빈 리스트를 반환해 서비스 분기를 단순화한다.
    public List<ShopifyOrderDto> getOrders() {
        return orders != null ? orders : Collections.emptyList();
    }
}
