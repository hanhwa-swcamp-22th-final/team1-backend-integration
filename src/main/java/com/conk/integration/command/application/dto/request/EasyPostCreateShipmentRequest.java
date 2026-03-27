package com.conk.integration.command.application.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EasyPostCreateShipmentRequest {

    private ShipmentBody shipment;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShipmentBody {

        @JsonProperty("to_address")
        private AddressBody toAddress;

        @JsonProperty("from_address")
        private AddressBody fromAddress;

        private ParcelBody parcel;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AddressBody {
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ParcelBody {
        private Double weight;  // oz
        private Double length;  // inches
        private Double width;
        private Double height;
    }
}
