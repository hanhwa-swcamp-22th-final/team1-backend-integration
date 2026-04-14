package com.conk.integration.command.application.dto.response;

import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * WMS 내부 송장 발급/조회 응답 DTO다.
 */
@Getter
@Builder
public class InternalLabelResponse {

    private String orderId;
    private String invoiceNo;
    private String trackingCode;
    private String carrierType;
    private String service;
    private Integer freightChargeAmt;
    private String shipToAddress;
    private String trackingUrl;
    private String labelFileUrl;
    private LocalDateTime issuedAt;

    public static InternalLabelResponse from(EasypostShipmentInvoice invoice) {
        return InternalLabelResponse.builder()
                .orderId(invoice.getOrderId())
                .invoiceNo(invoice.getInvoiceNo())
                .trackingCode(invoice.getTrackingCode())
                .carrierType(invoice.getCarrierType() == null ? null : invoice.getCarrierType().name())
                .service(invoice.getService())
                .freightChargeAmt(invoice.getFreightChargeAmt())
                .shipToAddress(invoice.getShipToAddress())
                .trackingUrl(invoice.getTrackingUrl())
                .labelFileUrl(invoice.getLabelFileUrl())
                .issuedAt(parseIssuedAt(invoice.getIssuedAt()))
                .build();
    }

    private static LocalDateTime parseIssuedAt(String issuedAt) {
        if (issuedAt == null || issuedAt.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(issuedAt);
    }
}
