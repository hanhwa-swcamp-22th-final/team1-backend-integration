package com.conk.integration.command.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PostageLabelDto {
        @JsonProperty("label_url")
        private String labelUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackerDto {
        private String id;
        @JsonProperty("public_url")
        private String publicUrl;
    }

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
