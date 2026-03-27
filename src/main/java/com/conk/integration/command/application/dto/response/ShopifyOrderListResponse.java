package com.conk.integration.command.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Setter
@NoArgsConstructor
public class ShopifyOrderListResponse {

    @JsonProperty("orders")
    private List<ShopifyOrderDto> orders;

    public List<ShopifyOrderDto> getOrders() {
        return orders != null ? orders : Collections.emptyList();
    }
}
