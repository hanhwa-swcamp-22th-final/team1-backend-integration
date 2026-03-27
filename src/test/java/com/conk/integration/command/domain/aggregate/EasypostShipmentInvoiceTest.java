package com.conk.integration.command.domain.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;

@DataJpaTest
@DisplayName("EasypostShipmentInvoice Entity Tests")
class EasypostShipmentInvoiceTest {

    @Autowired
    private EasypostShipmentInvoiceRepository repository;

    @Test
    @DisplayName("송장 생성 및 저장")
    void save_Invoice() {
        // given
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-001")
                .carrierType(CarrierType.USPS)
                .freightChargeAmt(1500)
                .shipToAddress("123 Main St, Los Angeles, CA")
                .trackingUrl("https://track.usps.com/INV-001")
                .labelFileUrl("https://label.easypost.com/INV-001.pdf")
                .build();

        // when
        EasypostShipmentInvoice saved = repository.save(invoice);

        // then
        assertThat(saved.getInvoiceNo()).isEqualTo("INV-001");
        assertThat(saved.getCarrierType()).isEqualTo(CarrierType.USPS);
        assertThat(saved.getFreightChargeAmt()).isEqualTo(1500);
    }

    @Test
    @DisplayName("invoice_no로 송장 조회")
    void findById_Invoice() {
        // given
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-002")
                .carrierType(CarrierType.FEDEX)
                .freightChargeAmt(2000)
                .shipToAddress("456 Oak Ave, New York, NY")
                .build();
        repository.save(invoice);

        // when
        Optional<EasypostShipmentInvoice> found = repository.findById("INV-002");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getCarrierType()).isEqualTo(CarrierType.FEDEX);
        assertThat(found.get().getFreightChargeAmt()).isEqualTo(2000);
    }

    @Test
    @DisplayName("없는 invoice_no 조회 시 empty 반환")
    void findById_NotFound_ReturnsEmpty() {
        // when
        Optional<EasypostShipmentInvoice> result = repository.findById("NONEXISTENT");

        // then
        assertThat(result).isEmpty();
    }
}
