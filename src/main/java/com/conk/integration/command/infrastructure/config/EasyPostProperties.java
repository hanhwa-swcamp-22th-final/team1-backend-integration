package com.conk.integration.command.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easypost")
@Getter
@Setter
public class EasyPostProperties {

    private String apiKey;
    private String baseUrl = "https://api.easypost.com";

    public String getShipmentsUrl() {
        return baseUrl + "/v2/shipments";
    }

    public String getBuyRateUrl(String shipmentId) {
        return baseUrl + "/v2/shipments/" + shipmentId + "/buy";
    }
}
