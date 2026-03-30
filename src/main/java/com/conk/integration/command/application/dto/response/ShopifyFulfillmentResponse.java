package com.conk.integration.command.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Shopify fulfillment 생성 후 주요 결과 필드만 읽어오는 응답 DTO다.
@Getter
@Setter
@NoArgsConstructor
public class ShopifyFulfillmentResponse {

    private FulfillmentBody fulfillment;

    // 실제 출고 생성 결과 본문이다.
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
