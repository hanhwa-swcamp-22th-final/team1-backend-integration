package com.conk.integration.command.application.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Shopify fulfillment 생성 요청 body를 표현한다.
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyFulfillmentRequest {

    private FulfillmentBody fulfillment;

    // Shopify가 기대하는 fulfillment 루트 객체다.
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FulfillmentBody {

        @JsonProperty("tracking_info")
        private TrackingInfo trackingInfo;

        @JsonProperty("notify_customer")
        private boolean notifyCustomer;
    }

    // 추적번호와 운송사 정보를 한 덩어리로 보낸다.
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrackingInfo {

        private String number;
        private String company;
    }
}
