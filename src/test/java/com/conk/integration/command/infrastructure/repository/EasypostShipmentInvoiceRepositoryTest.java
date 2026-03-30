package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.CarrierType;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("EasypostShipmentInvoiceRepository Tests")
class EasypostShipmentInvoiceRepositoryTest {

    @Autowired
    private EasypostShipmentInvoiceRepository invoiceRepository;

    @Test
    @DisplayName("Invoice를 저장하고 invoiceNo로 조회하면 동일한 데이터가 반환된다")
    void save_andFindById_returnsSameInvoice() {
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-TEST-001")
                .carrierType(CarrierType.USPS)
                .freightChargeAmt(2500)
                .shipToAddress("New York, NY 10001, US")
                .trackingUrl("https://track.easypost.com/TEST001")
                .labelFileUrl("https://cdn.easypost.com/label/TEST001.pdf")
                .build();

        invoiceRepository.save(invoice);

        EasypostShipmentInvoice found = invoiceRepository.findById("INV-TEST-001").orElseThrow();

        assertThat(found.getCarrierType()).isEqualTo(CarrierType.USPS);
        assertThat(found.getFreightChargeAmt()).isEqualTo(2500);
        assertThat(found.getTrackingUrl()).isEqualTo("https://track.easypost.com/TEST001");
    }

    @Test
    @DisplayName("존재하지 않는 invoiceNo로 조회하면 Optional.empty()가 반환된다")
    void findById_notExisting_returnsEmpty() {
        assertThat(invoiceRepository.findById("NOT-EXIST")).isEmpty();
    }
}
