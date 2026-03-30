package com.conk.integration.query.service;

import com.conk.integration.query.dto.SellerChannelOrderDto;
import com.conk.integration.query.dto.SellerChannelOrderQueryResult;
import com.conk.integration.query.mapper.SellerChannelOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 주문 조회 서비스의 DTO 변환 규칙과 상태/요약 계산을 검증한다.
@ExtendWith(MockitoExtension.class)
@DisplayName("query.SellerChannelOrderQueryService 단위 테스트")
class SellerChannelOrderQueryServiceTest {

    @Mock private SellerChannelOrderMapper channelOrderMapper;
    @InjectMocks private SellerChannelOrderQueryService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] mapper raw → DTO 변환 (id, channel, itemsSummary, status 검증)")
    void getOrders_mapsRawResultToDto() {
        // 대표 raw 결과 하나로 표시용 필드 변환을 한 번에 확인한다.
        LocalDateTime orderedAt = LocalDateTime.of(2026, 3, 19, 9, 12);
        SellerChannelOrderQueryResult raw = buildRaw("ORD-1", "SHOPIFY", "루미에르 앰플 30ml", 2, null, null, orderedAt);
        given(channelOrderMapper.findBySellerIdWithItemSummary("seller-1")).willReturn(List.of(raw));

        List<SellerChannelOrderDto> result = service.getOrders("seller-1");

        assertThat(result).hasSize(1);
        SellerChannelOrderDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo("ORD-1");
        assertThat(dto.getChannel()).isEqualTo("SHOPIFY");
        assertThat(dto.getItemsSummary()).isEqualTo("루미에르 앰플 30ml 외 1건");
        assertThat(dto.getStatus()).isEqualTo("NEW");
        assertThat(dto.getOrderedAt()).isEqualTo(orderedAt);
        verify(channelOrderMapper).findBySellerIdWithItemSummary("seller-1");
    }

    @Test
    @DisplayName("[GREEN] 빈 결과 → 빈 리스트 반환")
    void getOrders_returnsEmpty_whenMapperReturnsEmpty() {
        given(channelOrderMapper.findBySellerIdWithItemSummary("seller-1")).willReturn(List.of());

        assertThat(service.getOrders("seller-1")).isEmpty();
    }

    @Test
    @DisplayName("[GREEN] orderAmount 항상 null")
    void getOrders_orderAmountIsAlwaysNull() {
        // 현재 구현은 주문 금액을 채우지 않으므로 null 고정 동작을 드러낸다.
        given(channelOrderMapper.findBySellerIdWithItemSummary("seller-1"))
                .willReturn(List.of(buildRaw("ORD-1", "SHOPIFY", "상품A", 1, null, null, LocalDateTime.now())));

        assertThat(service.getOrders("seller-1").get(0).getOrderAmount()).isNull();
    }

    @Test
    @DisplayName("[GREEN] conkOrderNo = orderId")
    void getOrders_conkOrderNoEqualsOrderId() {
        given(channelOrderMapper.findBySellerIdWithItemSummary("seller-1"))
                .willReturn(List.of(buildRaw("ORD-999", "SHOPIFY", "상품A", 1, null, null, LocalDateTime.now())));

        SellerChannelOrderDto dto = service.getOrders("seller-1").get(0);
        assertThat(dto.getConkOrderNo()).isEqualTo("ORD-999");
        assertThat(dto.getId()).isEqualTo("ORD-999");
    }

    // ─────────────────────────────────────────────────────────
    // buildItemsSummary
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] buildItemsSummary: itemCount=1 → 상품명만 반환")
    void buildItemsSummary_singleItem() {
        assertThat(service.buildItemsSummary("루미에르 앰플", 1)).isEqualTo("루미에르 앰플");
    }

    @Test
    @DisplayName("[GREEN] buildItemsSummary: itemCount=2 → '상품명 외 1건'")
    void buildItemsSummary_twoItems() {
        assertThat(service.buildItemsSummary("루미에르 앰플", 2)).isEqualTo("루미에르 앰플 외 1건");
    }

    @Test
    @DisplayName("[GREEN] buildItemsSummary: itemCount=3 → '상품명 외 2건'")
    void buildItemsSummary_threeItems() {
        assertThat(service.buildItemsSummary("루미에르 앰플", 3)).isEqualTo("루미에르 앰플 외 2건");
    }

    @Test
    @DisplayName("[GREEN] buildItemsSummary: firstItemName null → 빈 문자열")
    void buildItemsSummary_nullFirstName_returnsEmpty() {
        assertThat(service.buildItemsSummary(null, 2)).isEqualTo("");
    }

    @Test
    @DisplayName("[GREEN] buildItemsSummary: firstItemName 공백 → 빈 문자열")
    void buildItemsSummary_blankFirstName_returnsEmpty() {
        assertThat(service.buildItemsSummary("   ", 2)).isEqualTo("");
    }

    // ─────────────────────────────────────────────────────────
    // resolveStatus
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] resolveStatus: invoiceNo null, shippedAt null → NEW")
    void resolveStatus_new() {
        assertThat(service.resolveStatus(null, null)).isEqualTo("NEW");
    }

    @Test
    @DisplayName("[GREEN] resolveStatus: invoiceNo 있음, shippedAt null → PROCESSING")
    void resolveStatus_processing() {
        assertThat(service.resolveStatus("shp_001", null)).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("[GREEN] resolveStatus: shippedAt 있음 → SHIPPED (invoiceNo 무관)")
    void resolveStatus_shipped() {
        assertThat(service.resolveStatus("shp_001", "2026-03-20")).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("[GREEN] resolveStatus: shippedAt 공백 → PROCESSING (공백은 미배송 처리)")
    void resolveStatus_blankShippedAt_treatedAsNotShipped() {
        assertThat(service.resolveStatus("shp_001", "   ")).isEqualTo("PROCESSING");
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] sellerId null → IllegalArgumentException, mapper 미호출")
    void getOrders_throwsWhenSellerIdNull() {
        assertThatThrownBy(() -> service.getOrders(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelOrderMapper);
    }

    @Test
    @DisplayName("[예외] sellerId 공백 → IllegalArgumentException, mapper 미호출")
    void getOrders_throwsWhenSellerIdBlank() {
        assertThatThrownBy(() -> service.getOrders("  "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelOrderMapper);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    // mapper raw 결과 fixture를 만들 때 기본 채널 주문 필드를 함께 채운다.
    private SellerChannelOrderQueryResult buildRaw(String orderId, String orderChannel,
                                                    String firstItemName, int itemCount,
                                                    String invoiceNo, String shippedAt,
                                                    LocalDateTime orderedAt) {
        SellerChannelOrderQueryResult raw = new SellerChannelOrderQueryResult();
        raw.setOrderId(orderId);
        raw.setChannelOrderNo("CH-" + orderId);
        raw.setOrderChannel(orderChannel);
        raw.setReceiverName("Test Receiver");
        raw.setOrderedAt(orderedAt);
        raw.setInvoiceNo(invoiceNo);
        raw.setShippedAt(shippedAt);
        raw.setFirstItemName(firstItemName);
        raw.setItemCount(itemCount);
        return raw;
    }
}
