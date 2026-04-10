package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.infrastructure.repository.EasypostShipmentInvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 송장 리포지토리는 저장 후 재조회와 미존재 조회 동작을 최소 범위로 본다.
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("EasypostShipmentInvoiceRepository 테스트")
class EasypostShipmentInvoiceRepositoryTest {

    @Autowired
    private EasypostShipmentInvoiceRepository invoiceRepository;

    // 저장 후 재조회로 주요 추적/금액 필드가 DB round-trip 후에도 유지되는지 확인한다.
    @Test
    @DisplayName("송장 정보가 주어지면 저장 후 invoiceNo로 조회했을 때 동일한 데이터를 반환해야 한다")
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

    // 미존재 송장 조회는 예외 대신 빈 Optional을 반환해야 서비스 분기가 단순하다.
    @Test
    @DisplayName("존재하지 않는 invoiceNo가 주어지면 조회했을 때 Optional.empty를 반환해야 한다")
    void findById_notExisting_returnsEmpty() {
        assertThat(invoiceRepository.findById("NOT-EXIST")).isEmpty();
    }
}
