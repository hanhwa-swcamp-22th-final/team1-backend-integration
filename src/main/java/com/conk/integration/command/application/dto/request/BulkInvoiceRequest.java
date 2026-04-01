package com.conk.integration.command.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// EasyPost 일괄 송장 발급 요청의 body를 표현한다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BulkInvoiceRequest {

    @JsonProperty("sellerId")
    private String sellerId;

    // 모든 주문에 공통으로 적용되는 발송 주소다.
    @JsonProperty("fromAddress")
    private EasyPostCreateShipmentRequest.AddressBody fromAddress;

    // 모든 주문에 공통으로 적용되는 소포 정보다.
    @JsonProperty("parcel")
    private EasyPostCreateShipmentRequest.ParcelBody parcel;
}
