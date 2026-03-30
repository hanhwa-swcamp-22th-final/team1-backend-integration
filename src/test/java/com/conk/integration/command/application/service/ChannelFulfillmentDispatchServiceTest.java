package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// fulfillment orchestration이 채널별 sender 선택과 예외 처리를 올바르게 수행하는지 본다.
@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelFulfillmentDispatchService 단위 테스트")
class ChannelFulfillmentDispatchServiceTest {

    @Mock private ChannelOrderRepository channelOrderRepository;
    @Mock private EasypostShipmentInvoiceRepository invoiceRepository;
    @Mock private ChannelFulfillmentSender shopifySender;

    @Test
    @DisplayName("[GREEN] SHOPIFY 주문이면 지원 sender를 선택해 send()를 호출한다")
    void fulfill_callsMatchedSender() {
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORD-001")
                .orderChannel(OrderChannel.SHOPIFY)
                .invoiceNo("INV-001")
                .sellerId("seller-001")
                .build();
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-001")
                .build();

        given(channelOrderRepository.findById("ORD-001")).willReturn(Optional.of(order));
        given(invoiceRepository.findById("INV-001")).willReturn(Optional.of(invoice));
        given(shopifySender.supports(OrderChannel.SHOPIFY)).willReturn(true);

        ChannelFulfillmentDispatchService service = new ChannelFulfillmentDispatchService(
                channelOrderRepository,
                invoiceRepository,
                List.of(shopifySender)
        );

        service.fulfill("ORD-001");

        verify(shopifySender).send(order, invoice);
    }

    @Test
    @DisplayName("[예외] 주문이 없으면 IllegalArgumentException")
    void fulfill_throwsWhenOrderNotFound() {
        given(channelOrderRepository.findById("NOT_EXIST")).willReturn(Optional.empty());

        ChannelFulfillmentDispatchService service = new ChannelFulfillmentDispatchService(
                channelOrderRepository,
                invoiceRepository,
                List.of(shopifySender)
        );

        assertThatThrownBy(() -> service.fulfill("NOT_EXIST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_EXIST");
    }

    @Test
    @DisplayName("[예외] invoiceNo가 없으면 IllegalStateException")
    void fulfill_throwsWhenInvoiceNoIsNull() {
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORD-002")
                .orderChannel(OrderChannel.SHOPIFY)
                .invoiceNo(null)
                .sellerId("seller-001")
                .build();
        given(channelOrderRepository.findById("ORD-002")).willReturn(Optional.of(order));

        ChannelFulfillmentDispatchService service = new ChannelFulfillmentDispatchService(
                channelOrderRepository,
                invoiceRepository,
                List.of(shopifySender)
        );

        assertThatThrownBy(() -> service.fulfill("ORD-002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("송장이 발급되지 않은 주문");

        verify(shopifySender, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("[예외] 송장 엔티티가 없으면 IllegalStateException")
    void fulfill_throwsWhenInvoiceNotFound() {
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORD-003")
                .orderChannel(OrderChannel.SHOPIFY)
                .invoiceNo("INV-MISSING")
                .sellerId("seller-001")
                .build();
        given(channelOrderRepository.findById("ORD-003")).willReturn(Optional.of(order));
        given(invoiceRepository.findById("INV-MISSING")).willReturn(Optional.empty());

        ChannelFulfillmentDispatchService service = new ChannelFulfillmentDispatchService(
                channelOrderRepository,
                invoiceRepository,
                List.of(shopifySender)
        );

        assertThatThrownBy(() -> service.fulfill("ORD-003"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INV-MISSING");
    }

    @Test
    @DisplayName("[예외] 지원 sender가 없으면 IllegalArgumentException")
    void fulfill_throwsWhenSenderNotSupported() {
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORD-004")
                .orderChannel(OrderChannel.AMAZON)
                .invoiceNo("INV-004")
                .sellerId("seller-001")
                .build();
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-004")
                .build();

        given(channelOrderRepository.findById("ORD-004")).willReturn(Optional.of(order));
        given(invoiceRepository.findById("INV-004")).willReturn(Optional.of(invoice));
        given(shopifySender.supports(OrderChannel.AMAZON)).willReturn(false);

        ChannelFulfillmentDispatchService service = new ChannelFulfillmentDispatchService(
                channelOrderRepository,
                invoiceRepository,
                List.of(shopifySender)
        );

        assertThatThrownBy(() -> service.fulfill("ORD-004"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 fulfillment 채널입니다");

        verify(shopifySender, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
