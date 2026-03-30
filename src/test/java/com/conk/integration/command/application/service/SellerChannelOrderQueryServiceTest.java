package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.SellerChannelOrderDto;
import com.conk.integration.command.domain.aggregate.*;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerChannelOrderQueryService 단위 테스트")
class SellerChannelOrderQueryServiceTest {

    @Mock private ChannelOrderRepository channelOrderRepository;

    @InjectMocks
    private SellerChannelOrderQueryService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] 정상 플로우 - 주문 목록 반환")
    void getOrders_returnsOrderList() {
        String sellerId = "seller-001";
        ChannelOrder order = buildOrderWithItems("ORD-001", sellerId, OrderChannel.SHOPIFY,
                null, null, List.of("루미에르 앰플 30ml", "토너 100ml"));

        given(channelOrderRepository.findBySellerIdWithItems(sellerId)).willReturn(List.of(order));

        List<SellerChannelOrderDto> result = service.getOrders(sellerId);

        assertThat(result).hasSize(1);
        SellerChannelOrderDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo("ORD-001");
        assertThat(dto.getConkOrderNo()).isEqualTo("ORD-001");
        assertThat(dto.getChannel()).isEqualTo("SHOPIFY");
        assertThat(dto.getRecipient()).isEqualTo("Emily Harris");
        assertThat(dto.getItemsSummary()).isEqualTo("루미에르 앰플 30ml 외 1건");
    }

    @Test
    @DisplayName("[GREEN] 주문이 없으면 빈 리스트 반환")
    void getOrders_returnsEmpty_whenNoOrders() {
        given(channelOrderRepository.findBySellerIdWithItems("seller-002")).willReturn(List.of());

        List<SellerChannelOrderDto> result = service.getOrders("seller-002");

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────
    // itemsSummary 생성
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] 상품 1건 → 상품명만 반환")
    void buildItemsSummary_singleItem() {
        List<ChannelOrderItem> items = List.of(item("루미에르 앰플 30ml", 1));
        assertThat(service.buildItemsSummary(items)).isEqualTo("루미에르 앰플 30ml");
    }

    @Test
    @DisplayName("[GREEN] 상품 2건 → '{첫번째} 외 1건'")
    void buildItemsSummary_twoItems() {
        List<ChannelOrderItem> items = List.of(
                item("루미에르 앰플 30ml", 1),
                item("토너 100ml", 2)
        );
        assertThat(service.buildItemsSummary(items)).isEqualTo("루미에르 앰플 30ml 외 1건");
    }

    @Test
    @DisplayName("[GREEN] 상품 3건 → '{첫번째} 외 2건'")
    void buildItemsSummary_threeItems() {
        List<ChannelOrderItem> items = List.of(
                item("루미에르 앰플 30ml", 1),
                item("토너 100ml", 1),
                item("세럼 50ml", 1)
        );
        assertThat(service.buildItemsSummary(items)).isEqualTo("루미에르 앰플 30ml 외 2건");
    }

    @Test
    @DisplayName("[GREEN] 상품 없으면 빈 문자열 반환")
    void buildItemsSummary_empty() {
        assertThat(service.buildItemsSummary(List.of())).isEmpty();
    }

    // ─────────────────────────────────────────────────────────
    // status 도출
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] invoiceNo null → status 'NEW'")
    void resolveStatus_new_whenNoInvoice() {
        assertThat(service.resolveStatus(null, null)).isEqualTo("NEW");
    }

    @Test
    @DisplayName("[GREEN] invoiceNo 있음, shippedAt null → status 'PROCESSING'")
    void resolveStatus_processing_whenInvoiceButNotShipped() {
        assertThat(service.resolveStatus("inv-001", null)).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("[GREEN] shippedAt 있음 → status 'SHIPPED'")
    void resolveStatus_shipped_whenShippedAtSet() {
        assertThat(service.resolveStatus("inv-001", "2026-03-27T10:00:00")).isEqualTo("SHIPPED");
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] sellerId null → IllegalArgumentException")
    void getOrders_throwsWhenSellerIdNull() {
        assertThatThrownBy(() -> service.getOrders(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[예외] sellerId 빈 문자열 → IllegalArgumentException")
    void getOrders_throwsWhenSellerIdBlank() {
        assertThatThrownBy(() -> service.getOrders(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private ChannelOrder buildOrderWithItems(String orderId, String sellerId, OrderChannel channel,
                                              String invoiceNo, String shippedAt, List<String> productNames) {
        ChannelOrder order = ChannelOrder.builder()
                .orderId(orderId)
                .channelOrderNo("CH-" + orderId)
                .sellerId(sellerId)
                .orderChannel(channel)
                .invoiceNo(invoiceNo)
                .shippedAt(shippedAt)
                .receiverName("Emily Harris")
                .orderedAt(LocalDateTime.of(2026, 3, 19, 9, 12))
                .build();

        List<ChannelOrderItem> items = new ArrayList<>();
        for (int i = 0; i < productNames.size(); i++) {
            items.add(item(productNames.get(i), 1));
        }
        items.forEach(order::addItem);
        return order;
    }

    private ChannelOrderItem item(String productName, int quantity) {
        return ChannelOrderItem.builder()
                .id(new ChannelOrderItemId("ORD-001", "SKU-" + productName.hashCode()))
                .productNameSnapshot(productName)
                .quantity(quantity)
                .build();
    }
}
