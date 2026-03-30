package com.conk.integration.command.domain.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EasypostShipmentInvoice Entity Tests")
class EasypostShipmentInvoiceTest {

    @Test
    @DisplayName("빌더로 생성하면 배송 추적 관련 필드가 유지된다")
    void builder_setsTrackingAndIssueFields() {
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-001")
                .carrierType(CarrierType.USPS)
                .freightChargeAmt(1500)
                .shipToAddress("123 Main St, Los Angeles, CA")
                .trackingUrl("https://track.usps.com/INV-001")
                .labelFileUrl("https://label.easypost.com/INV-001.pdf")
                .issuedAt("2026-03-30T09:00:00Z")
                .handoverAt("2026-03-30T09:30:00Z")
                .build();

        assertThat(invoice.getInvoiceNo()).isEqualTo("INV-001");
        assertThat(invoice.getCarrierType()).isEqualTo(CarrierType.USPS);
        assertThat(invoice.getFreightChargeAmt()).isEqualTo(1500);
        assertThat(invoice.getTrackingUrl()).isEqualTo("https://track.usps.com/INV-001");
        assertThat(invoice.getIssuedAt()).isEqualTo("2026-03-30T09:00:00Z");
        assertThat(invoice.getHandoverAt()).isEqualTo("2026-03-30T09:30:00Z");
    }

    @Test
    @DisplayName("송장 주소와 라벨 URL을 개별 필드로 보존한다")
    void builder_keepsAddressAndLabelUrl() {
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-002")
                .carrierType(CarrierType.FEDEX)
                .freightChargeAmt(2000)
                .shipToAddress("456 Oak Ave, New York, NY")
                .labelFileUrl("https://label.easypost.com/INV-002.pdf")
                .build();

        assertThat(invoice.getShipToAddress()).isEqualTo("456 Oak Ave, New York, NY");
        assertThat(invoice.getLabelFileUrl()).isEqualTo("https://label.easypost.com/INV-002.pdf");
    }
}
