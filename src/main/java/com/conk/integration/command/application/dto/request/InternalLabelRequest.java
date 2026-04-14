package com.conk.integration.command.application.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WMS 내부 송장 발급 요청 DTO다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InternalLabelRequest {

    private String orderId;
    private String carrier;
    private String service;
    private String labelFormat;
    private AddressDto toAddress;
    private AddressDto fromAddress;
    private ParcelDto parcel;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AddressDto {
        private String name;
        private String street1;
        private String street2;
        private String city;
        private String state;
        private String zip;
        private String country;
        private String phone;
        private String email;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ParcelDto {
        private Double weight;
        private Double length;
        private Double width;
        private Double height;
    }
}
