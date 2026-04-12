package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.repository.EasypostShipmentInvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoicePersistenceService 테스트")
class InvoicePersistenceServiceTest {

    @Mock private EasypostShipmentInvoiceRepository invoiceRepository;
    @Mock private ChannelOrderRepository channelOrderRepository;

    @InjectMocks
    private InvoicePersistenceService service;

    @Test
    @DisplayName("송장과 주문이 주어지면 송장을 저장했을 때 주문에 송장 번호를 할당하고 함께 저장해야 한다")
    void saveInvoiceAndAssign_happyPath() {
        EasypostShipmentInvoice invoice = buildInvoice("shp_001");
        ChannelOrder order = buildOrder("ORD-001");

        given(invoiceRepository.save(invoice)).willReturn(invoice);
        given(channelOrderRepository.save(any())).willReturn(order);

        EasypostShipmentInvoice result = service.saveInvoiceAndAssign(invoice, order);

        assertThat(result.getInvoiceNo()).isEqualTo("shp_001");
        assertThat(order.getInvoiceNo()).isEqualTo("shp_001");
        verify(invoiceRepository).save(invoice);
        verify(channelOrderRepository).save(order);
    }

    @Test
    @DisplayName("송장 저장에 실패하면 송장을 저장했을 때 예외를 전파하고 주문 저장을 호출하지 않아야 한다")
    void saveInvoiceAndAssign_invoiceRepoFails_propagatesAndSkipsOrderSave() {
        EasypostShipmentInvoice invoice = buildInvoice("shp_dup");
        ChannelOrder order = buildOrder("ORD-002");

        given(invoiceRepository.save(invoice))
                .willThrow(new DataIntegrityViolationException("Duplicate entry 'shp_dup'"));

        assertThatThrownBy(() -> service.saveInvoiceAndAssign(invoice, order))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("shp_dup");

        verify(channelOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("송장 저장 후 주문 저장에 실패하면 송장을 저장했을 때 예외를 전파해야 한다")
    void saveInvoiceAndAssign_orderRepoFails_propagates() {
        EasypostShipmentInvoice invoice = buildInvoice("shp_002");
        ChannelOrder order = buildOrder("ORD-003");

        given(invoiceRepository.save(invoice)).willReturn(invoice);
        given(channelOrderRepository.save(any()))
                .willThrow(new RuntimeException("DB 연결 오류"));

        assertThatThrownBy(() -> service.saveInvoiceAndAssign(invoice, order))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 연결 오류");
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private EasypostShipmentInvoice buildInvoice(String invoiceNo) {
        return EasypostShipmentInvoice.builder()
                .invoiceNo(invoiceNo)
                .carrierType(CarrierType.USPS)
                .freightChargeAmt(550)
                .trackingUrl("https://track.easypost.com/" + invoiceNo)
                .labelFileUrl("https://label.url/" + invoiceNo + ".pdf")
                .build();
    }

    private ChannelOrder buildOrder(String orderId) {
        return ChannelOrder.builder()
                .orderId(orderId)
                .orderChannel(OrderChannel.MANUAL)
                .sellerId("seller-001")
                .build();
    }
}

