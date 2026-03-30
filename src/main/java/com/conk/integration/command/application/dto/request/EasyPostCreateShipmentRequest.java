package com.conk.integration.command.application.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// EasyPost shipment 생성 요청의 최소 JSON 구조를 표현한다.
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EasyPostCreateShipmentRequest {

    private ShipmentBody shipment;

    // EasyPost가 요구하는 shipment 루트 객체다.
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

    // 수취/발송 주소를 같은 구조로 재사용한다.
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

    // 소포 크기와 무게 정보를 담는다.
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
