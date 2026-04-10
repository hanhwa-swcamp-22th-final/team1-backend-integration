package com.conk.integration.query.service;

import com.conk.integration.common.exception.BusinessException;
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
@DisplayName("SellerChannelOrderQueryService 테스트")
class SellerChannelOrderQueryServiceTest {

    @Mock private SellerChannelOrderMapper channelOrderMapper;
    @InjectMocks private SellerChannelOrderQueryService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("주문 조회 결과가 주어지면 주문 목록을 조회했을 때 DTO로 변환해 반환해야 한다")
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
    @DisplayName("주문 조회 결과가 비어 있으면 주문 목록을 조회했을 때 빈 리스트를 반환해야 한다")
    void getOrders_returnsEmpty_whenMapperReturnsEmpty() {
        given(channelOrderMapper.findBySellerIdWithItemSummary("seller-1")).willReturn(List.of());

        assertThat(service.getOrders("seller-1")).isEmpty();
    }

    @Test
    @DisplayName("주문 조회 결과가 주어지면 주문 목록을 조회했을 때 orderAmount는 null이어야 한다")
    void getOrders_orderAmountIsAlwaysNull() {
        // 현재 구현은 주문 금액을 채우지 않으므로 null 고정 동작을 드러낸다.
        given(channelOrderMapper.findBySellerIdWithItemSummary("seller-1"))
                .willReturn(List.of(buildRaw("ORD-1", "SHOPIFY", "상품A", 1, null, null, LocalDateTime.now())));

        assertThat(service.getOrders("seller-1").get(0).getOrderAmount()).isNull();
    }

    @Test
    @DisplayName("주문 조회 결과가 주어지면 주문 목록을 조회했을 때 conkOrderNo는 orderId와 같아야 한다")
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
    @DisplayName("itemCount가 1이면 상품 요약을 생성했을 때 상품명만 반환해야 한다")
    void buildItemsSummary_singleItem() {
        assertThat(service.buildItemsSummary("루미에르 앰플", 1)).isEqualTo("루미에르 앰플");
    }

    @Test
    @DisplayName("itemCount가 2이면 상품 요약을 생성했을 때 상품명 외 1건 형식으로 반환해야 한다")
    void buildItemsSummary_twoItems() {
        assertThat(service.buildItemsSummary("루미에르 앰플", 2)).isEqualTo("루미에르 앰플 외 1건");
    }

    @Test
    @DisplayName("itemCount가 3이면 상품 요약을 생성했을 때 상품명 외 2건 형식으로 반환해야 한다")
    void buildItemsSummary_threeItems() {
        assertThat(service.buildItemsSummary("루미에르 앰플", 3)).isEqualTo("루미에르 앰플 외 2건");
    }

    @Test
    @DisplayName("firstItemName이 null이면 상품 요약을 생성했을 때 빈 문자열을 반환해야 한다")
    void buildItemsSummary_nullFirstName_returnsEmpty() {
        assertThat(service.buildItemsSummary(null, 2)).isEqualTo("");
    }

    @Test
    @DisplayName("firstItemName이 공백이면 상품 요약을 생성했을 때 빈 문자열을 반환해야 한다")
    void buildItemsSummary_blankFirstName_returnsEmpty() {
        assertThat(service.buildItemsSummary("   ", 2)).isEqualTo("");
    }

    // ─────────────────────────────────────────────────────────
    // resolveStatus
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("invoiceNo와 shippedAt이 모두 null이면 주문 상태를 계산했을 때 NEW를 반환해야 한다")
    void resolveStatus_new() {
        assertThat(service.resolveStatus(null, null)).isEqualTo("NEW");
    }

    @Test
    @DisplayName("invoiceNo가 있고 shippedAt이 null이면 주문 상태를 계산했을 때 PROCESSING을 반환해야 한다")
    void resolveStatus_processing() {
        assertThat(service.resolveStatus("shp_001", null)).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("shippedAt이 있으면 주문 상태를 계산했을 때 invoiceNo와 관계없이 SHIPPED를 반환해야 한다")
    void resolveStatus_shipped() {
        assertThat(service.resolveStatus("shp_001", "2026-03-20")).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("shippedAt이 공백이면 주문 상태를 계산했을 때 PROCESSING을 반환해야 한다")
    void resolveStatus_blankShippedAt_treatedAsNotShipped() {
        assertThat(service.resolveStatus("shp_001", "   ")).isEqualTo("PROCESSING");
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("sellerId가 null이면 주문 목록을 조회했을 때 BusinessException(INT-001)을 발생시키고 mapper를 호출하지 않아야 한다")
    void getOrders_throwsWhenSellerIdNull() {
        assertThatThrownBy(() -> service.getOrders(null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(channelOrderMapper);
    }

    @Test
    @DisplayName("sellerId가 공백이면 주문 목록을 조회했을 때 BusinessException(INT-001)을 발생시키고 mapper를 호출하지 않아야 한다")
    void getOrders_throwsWhenSellerIdBlank() {
        assertThatThrownBy(() -> service.getOrders("  "))
                .isInstanceOf(BusinessException.class);
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
