package com.conk.integration.command.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Shopify 스토어명, 토큰, API 버전에서 호출 URL을 조합한다.
@ConfigurationProperties(prefix = "shopify")
@Getter
@Setter
public class ShopifyProperties {

    private String storeName;
    private String accessToken;
    private String apiVersion = "2025-01";

    // 스토어 단위 기본 URL.
    public String getBaseUrl() {
        return "https://" + storeName + ".myshopify.com";
    }

    // 주문 조회 엔드포인트.
    public String getOrdersUrl() {
        return getBaseUrl() + "/admin/api/" + apiVersion + "/orders.json";
    }

    // 주문별 fulfillment 생성 엔드포인트.
    public String getFulfillmentsUrl(String shopifyOrderId) {
        return getBaseUrl() + "/admin/api/" + apiVersion + "/orders/" + shopifyOrderId + "/fulfillments.json";
    }
}
