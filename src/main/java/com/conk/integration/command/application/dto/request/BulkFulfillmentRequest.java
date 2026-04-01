package com.conk.integration.command.application.dto.request;

import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 미전송 주문 일괄 fulfillment 요청 body를 표현한다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BulkFulfillmentRequest {

    @JsonProperty("sellerId")
    private String sellerId;

    @JsonProperty("orderChannel")
    private OrderChannel orderChannel;
}
