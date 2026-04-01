package com.conk.integration.command.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Shopify API 버전과 URL 조합 유틸리티를 제공한다.
// storeName과 accessToken은 ChannelApi 테이블에서 sellerId 기준으로 동적으로 조회한다.
@ConfigurationProperties(prefix = "shopify")
@Getter
@Setter
public class ShopifyProperties {

    private String apiVersion = "2025-01";

    // 스토어 단위 기본 URL.
    public String getBaseUrl(String storeName) {
        return "https://" + storeName + ".myshopify.com";
    }

    // GraphQL Admin API 엔드포인트.
    public String getGraphQLUrl(String storeName) {
        return getBaseUrl(storeName) + "/admin/api/" + apiVersion + "/graphql.json";
    }

    // 주문별 fulfillment 생성 엔드포인트.
    public String getFulfillmentsUrl(String storeName, String shopifyOrderId) {
        return getBaseUrl(storeName) + "/admin/api/" + apiVersion + "/orders/" + shopifyOrderId + "/fulfillments.json";
    }
}
