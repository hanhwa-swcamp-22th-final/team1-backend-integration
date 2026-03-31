package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.BulkFulfillmentResponse;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.query.dto.FulfillmentTargetDto;
import com.conk.integration.query.mapper.ChannelFulfillmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// fulfillment orchestration이 채널별 sender 선택과 예외 처리를 올바르게 수행하는지 본다.
@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelFulfillmentDispatchService 단위 테스트")
class ChannelFulfillmentDispatchServiceTest {

    @Mock private ChannelOrderRepository channelOrderRepository;
    @Mock private EasypostShipmentInvoiceRepository invoiceRepository;
    @Mock private ChannelFulfillmentMapper channelFulfillmentMapper;
    @Mock private ChannelFulfillmentSender shopifySender;

    // ─────────────────────────────────────────────────────────
    // fulfill() — 단건
    // ─────────────────────────────────────────────────────────

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

        service().fulfill("ORD-001");

        verify(shopifySender).send(order, invoice);
    }

    @Test
    @DisplayName("[예외] 주문이 없으면 IllegalArgumentException")
    void fulfill_throwsWhenOrderNotFound() {
        given(channelOrderRepository.findById("NOT_EXIST")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().fulfill("NOT_EXIST"))
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

        assertThatThrownBy(() -> service().fulfill("ORD-002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("송장이 발급되지 않은 주문");

        verify(shopifySender, never()).send(any(), any());
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

        assertThatThrownBy(() -> service().fulfill("ORD-003"))
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

        assertThatThrownBy(() -> service().fulfill("ORD-004"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 fulfillment 채널입니다");

        verify(shopifySender, never()).send(any(), any());
    }

    // ─────────────────────────────────────────────────────────
    // fulfillBulk() — 다건
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] 미전송 대상이 없으면 successCount=0, failCount=0 반환")
    void fulfillBulk_returnsZero_whenNoUnsyncedTargets() {
        given(channelFulfillmentMapper.findUnsyncedTargets("seller-001", "SHOPIFY"))
                .willReturn(List.of());

        BulkFulfillmentResponse response = service().fulfillBulk("seller-001", OrderChannel.SHOPIFY);

        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getFailCount()).isZero();
        verify(shopifySender, never()).sendBulk(any());
        verify(channelOrderRepository, never()).markAllSynced(any());
    }

    @Test
    @DisplayName("[GREEN] 미전송 대상 존재 시 sendBulk 호출 후 markAllSynced 호출")
    void fulfillBulk_callsSendBulkAndMarksSynced() {
        List<FulfillmentTargetDto> targets = List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "INV-A"),
                buildTarget("ORD-B", "gid://shopify/FulfillmentOrder/2", "INV-B")
        );
        given(channelFulfillmentMapper.findUnsyncedTargets("seller-001", "SHOPIFY"))
                .willReturn(targets);
        given(shopifySender.supports(OrderChannel.SHOPIFY)).willReturn(true);

        BulkFulfillmentResponse response = service().fulfillBulk("seller-001", OrderChannel.SHOPIFY);

        verify(shopifySender).sendBulk(targets);
        verify(channelOrderRepository).markAllSynced(List.of("ORD-A", "ORD-B"));
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getFailCount()).isZero();
    }

    @Test
    @DisplayName("[예외] sendBulk 실패 시 markAllSynced를 호출하지 않는다")
    void fulfillBulk_doesNotMarkSynced_whenSendBulkThrows() {
        List<FulfillmentTargetDto> targets = List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "INV-A")
        );
        given(channelFulfillmentMapper.findUnsyncedTargets("seller-001", "SHOPIFY"))
                .willReturn(targets);
        given(shopifySender.supports(OrderChannel.SHOPIFY)).willReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("Shopify 연결 실패"))
                .when(shopifySender).sendBulk(targets);

        assertThatThrownBy(() -> service().fulfillBulk("seller-001", OrderChannel.SHOPIFY))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Shopify 연결 실패");

        verify(channelOrderRepository, never()).markAllSynced(any());
    }

    @Test
    @DisplayName("[예외] mapper 조회 중 예외 발생 시 호출자에게 전파된다")
    void fulfillBulk_propagatesException_whenMapperThrows() {
        given(channelFulfillmentMapper.findUnsyncedTargets("seller-001", "SHOPIFY"))
                .willThrow(new RuntimeException("DB 연결 오류"));

        assertThatThrownBy(() -> service().fulfillBulk("seller-001", OrderChannel.SHOPIFY))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 연결 오류");

        verify(shopifySender, never()).sendBulk(any());
        verify(channelOrderRepository, never()).markAllSynced(any());
    }

    @Test
    @DisplayName("[예외] 지원 sender가 없으면 sendBulk를 호출하지 않고 예외 발생")
    void fulfillBulk_throwsWhenNoSenderSupports() {
        List<FulfillmentTargetDto> targets = List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "INV-A")
        );
        given(channelFulfillmentMapper.findUnsyncedTargets("seller-001", "SHOPIFY"))
                .willReturn(targets);
        given(shopifySender.supports(OrderChannel.SHOPIFY)).willReturn(false);

        assertThatThrownBy(() -> service().fulfillBulk("seller-001", OrderChannel.SHOPIFY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 fulfillment 채널입니다");

        verify(shopifySender, never()).sendBulk(any());
        verify(channelOrderRepository, never()).markAllSynced(any());
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private ChannelFulfillmentDispatchService service() {
        return new ChannelFulfillmentDispatchService(
                channelOrderRepository,
                invoiceRepository,
                channelFulfillmentMapper,
                List.of(shopifySender)
        );
    }

    private FulfillmentTargetDto buildTarget(String orderId, String fulfillmentOrderId, String invoiceNo) {
        FulfillmentTargetDto dto = new FulfillmentTargetDto();
        dto.setOrderId(orderId);
        dto.setFulfillmentOrderId(fulfillmentOrderId);
        dto.setInvoiceNo(invoiceNo);
        dto.setCarrierType("USPS");
        return dto;
    }
}
