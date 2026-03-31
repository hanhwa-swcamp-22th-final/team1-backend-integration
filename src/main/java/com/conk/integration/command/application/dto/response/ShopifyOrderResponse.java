package com.conk.integration.command.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// Shopify GraphQL 주문 조회 응답을 edges/node 구조로 매핑한다.
@Getter
@Setter
@NoArgsConstructor
public class ShopifyOrderResponse {

    @JsonProperty("data")
    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Data {

        @JsonProperty("orders")
        private OrderConnection orders;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OrderConnection {

        @JsonProperty("edges")
        private List<OrderEdge> edges;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OrderEdge {

        @JsonProperty("node")
        private OrderNode node;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OrderNode {

        // GID 형식: "gid://shopify/Order/12345"
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;

        @JsonProperty("createdAt")
        private String createdAt;

        @JsonProperty("shippingAddress")
        private ShippingAddress shippingAddress;

        @JsonProperty("fulfillmentOrders")
        private FulfillmentOrderConnection fulfillmentOrders;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ShippingAddress {

        @JsonProperty("name")
        private String name;

        @JsonProperty("firstName")
        private String firstName;

        @JsonProperty("lastName")
        private String lastName;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address1")
        private String address1;

        @JsonProperty("address2")
        private String address2;

        @JsonProperty("city")
        private String city;

        @JsonProperty("provinceCode")
        private String provinceCode;

        @JsonProperty("zip")
        private String zip;

        @JsonProperty("countryCode")
        private String countryCode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FulfillmentOrderConnection {

        @JsonProperty("edges")
        private List<FulfillmentOrderEdge> edges;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FulfillmentOrderEdge {

        @JsonProperty("node")
        private FulfillmentOrderNode node;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FulfillmentOrderNode {

        // GID 형식: "gid://shopify/FulfillmentOrder/67890"
        @JsonProperty("id")
        private String id;
    }
}
