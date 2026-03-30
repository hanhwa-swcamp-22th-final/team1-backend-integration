package com.conk.integration.command.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ShopifyFulfillmentResponse {

    private FulfillmentBody fulfillment;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FulfillmentBody {

        private Long id;
        private String status;

        @JsonProperty("tracking_number")
        private String trackingNumber;

        @JsonProperty("tracking_company")
        private String trackingCompany;
    }
}
