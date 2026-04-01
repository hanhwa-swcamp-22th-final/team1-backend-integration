package com.conk.integration.command.application.dto.response;

import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import lombok.AllArgsConstructor;
import lombok.Getter;

// EasyPost 단건 송장 발급 결과를 클라이언트에 노출하는 응답 DTO다.
@Getter
@AllArgsConstructor
public class EasyPostInvoiceResponse {

    private String invoiceNo;
    private String carrierType;
    private int freightChargeAmt;
    private String shipToAddress;
    private String trackingUrl;
    private String labelFileUrl;

    public static EasyPostInvoiceResponse from(EasypostShipmentInvoice invoice) {
        return new EasyPostInvoiceResponse(
                invoice.getInvoiceNo(),
                invoice.getCarrierType().name(),
                invoice.getFreightChargeAmt(),
                invoice.getShipToAddress(),
                invoice.getTrackingUrl(),
                invoice.getLabelFileUrl()
        );
    }
}