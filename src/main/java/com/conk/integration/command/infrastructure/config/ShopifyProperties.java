package com.conk.integration.command.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopify")
@Getter
@Setter
public class ShopifyProperties {

    private String storeName;
    private String accessToken;
    private String apiVersion = "2025-01";

    public String getBaseUrl() {
        return "https://" + storeName + ".myshopify.com";
    }

    public String getOrdersUrl() {
        return getBaseUrl() + "/admin/api/" + apiVersion + "/orders.json";
    }
}
