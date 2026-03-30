package com.conk.integration.command.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// EasyPost API 호출에 필요한 기본 URL과 인증 키를 보관한다.
@ConfigurationProperties(prefix = "easypost")
@Getter
@Setter
public class EasyPostProperties {

    private String apiKey;
    private String baseUrl = "https://api.easypost.com";

    // shipment 생성 엔드포인트.
    public String getShipmentsUrl() {
        return baseUrl + "/v2/shipments";
    }

    // shipment별 buyRate 엔드포인트.
    public String getBuyRateUrl(String shipmentId) {
        return baseUrl + "/v2/shipments/" + shipmentId + "/buy";
    }
}
