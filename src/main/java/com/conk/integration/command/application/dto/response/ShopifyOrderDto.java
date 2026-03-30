package com.conk.integration.command.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Shopify 주문 조회 응답에서 내부 주문 저장에 필요한 필드만 매핑한다.
@Getter
@Setter
@NoArgsConstructor
public class ShopifyOrderDto {

    @JsonProperty("id")
    private Long id;

    // 주문 번호 (예: "#1001")
    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("financial_status")
    private String financialStatus;

    @JsonProperty("fulfillment_status")
    private String fulfillmentStatus;

    @JsonProperty("shipping_address")
    private ShippingAddress shippingAddress;

    // 배송지 필드는 ChannelOrder 주소 컬럼으로 분해 저장된다.
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ShippingAddress {

        @JsonProperty("name")
        private String name;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address1")
        private String address1;

        @JsonProperty("address2")
        private String address2;

        @JsonProperty("city")
        private String city;

        @JsonProperty("province_code")
        private String provinceCode;

        @JsonProperty("zip")
        private String zip;

        @JsonProperty("country_code")
        private String countryCode;
    }
}
