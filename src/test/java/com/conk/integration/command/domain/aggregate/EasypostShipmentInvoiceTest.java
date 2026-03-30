package com.conk.integration.command.domain.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 송장 엔티티는 추적용 문자열 필드와 금액 필드 보존에 초점을 둔다.
@DisplayName("EasypostShipmentInvoice Entity Tests")
class EasypostShipmentInvoiceTest {

    // 출고 흐름에서 직접 소비되는 추적 URL과 시각 필드가 깨지지 않는지 확인한다.
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

    // 주소와 라벨 URL은 외부 API 호출에 그대로 전달될 수 있어 값 보존이 중요하다.
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
