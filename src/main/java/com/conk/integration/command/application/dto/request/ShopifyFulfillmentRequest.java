package com.conk.integration.command.application.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyFulfillmentRequest {

    private FulfillmentBody fulfillment;

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
