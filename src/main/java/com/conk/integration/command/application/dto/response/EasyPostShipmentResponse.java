package com.conk.integration.command.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// EasyPost shipment 관련 응답에서 서비스가 사용하는 필드만 매핑한다.
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EasyPostShipmentResponse {

    private String id;
    private String status;
    private String mode;

    @JsonProperty("tracking_code")
    private String trackingCode;

    private List<RateDto> rates;

    @JsonProperty("selected_rate")
    private RateDto selectedRate;

    @JsonProperty("postage_label")
    private PostageLabelDto postageLabel;

    private TrackerDto tracker;

    @JsonProperty("to_address")
    private AddressDto toAddress;

    // 운임 비교와 구매에 사용하는 rate 항목이다.
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RateDto {
        private String id;
        private String carrier;
        private String service;
        private String rate;
        private String currency;
    }

    // 구매 후 생성되는 라벨 URL 정보다.
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostageLabelDto {
        @JsonProperty("label_url")
        private String labelUrl;
    }

    // 배송 추적 공개 URL을 담는다.
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackerDto {
        private String id;
        @JsonProperty("public_url")
        private String publicUrl;
    }

    // 수취 주소를 송장 엔티티의 한 줄 주소로 변환할 때 사용한다.
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddressDto {
        private String name;
        private String street1;
        private String city;
        private String state;
        private String zip;
        private String country;
    }
}
