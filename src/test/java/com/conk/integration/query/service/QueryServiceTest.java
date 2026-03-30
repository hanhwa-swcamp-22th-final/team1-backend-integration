package com.conk.integration.query.service;

import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.query.dto.SellerChannelOrderDto;
import com.conk.integration.query.dto.SellerChannelOrderQueryResult;
import com.conk.integration.query.mapper.SellerChannelCardMapper;
import com.conk.integration.query.mapper.SellerChannelOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * [3단계] Query Service 단위 테스트 — Mockito
 *
 * - @ExtendWith(MockitoExtension.class) : spring Context 없는 경량 Mockito 환경
 * - MyBatis Mapper를 Mock으로 대체하여 Query Service 로직 자체에 집중
 *
 * ※ package-private 메서드(buildItemsSummary, resolveStatus, toLabel) 접근을 위해
 *   테스트 패키지를 실제 소스와 동일한 패키지 (query.service)로 배치
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[Service] Query Service 단위 테스트 (Mockito)")
class QueryServiceTest {

    /* ===================================================================
     * SellerChannelOrderQueryService
     * =================================================================== */

    @Nested
    @DisplayName("SellerChannelOrderQueryService")
    class SellerChannelOrderQueryServiceTests {

        @Mock
        private SellerChannelOrderMapper channelOrderMapper;

        @InjectMocks
        private SellerChannelOrderQueryService sellerChannelOrderQueryService;

        @Test
        @DisplayName("getOrders() — Mapper 결과를 SellerChannelOrderDto 리스트로 변환해 반환한다")
        void getOrders_returnsMappedDtos() {
            // given
            SellerChannelOrderQueryResult raw = new SellerChannelOrderQueryResult();
            raw.setOrderId("O-001");
            raw.setChannelOrderNo("#1001");
            raw.setOrderChannel("SHOPIFY");
            raw.setOrderedAt(LocalDateTime.of(2024, 1, 15, 10, 0));
            raw.setReceiverName("홍길동");
            raw.setFirstItemName("상품A");
            raw.setItemCount(3);
            raw.setInvoiceNo("INV-001");
            raw.setShippedAt(null);

            given(channelOrderMapper.findBySellerIdWithItemSummary("seller-A"))
                    .willReturn(List.of(raw));

            // when
            List<SellerChannelOrderDto> result = sellerChannelOrderQueryService.getOrders("seller-A");

            // then
            assertThat(result).hasSize(1);
            SellerChannelOrderDto dto = result.get(0);
            assertThat(dto.getId()).isEqualTo("O-001");
            assertThat(dto.getChannel()).isEqualTo("SHOPIFY");

            // conkOrderNo는 orderId를 그대로 사용
            assertThat(dto.getConkOrderNo()).isEqualTo("O-001");
            assertThat(dto.getItemsSummary()).isEqualTo("상품A 외 2건");
            assertThat(dto.getStatus()).isEqualTo("PROCESSING"); // invoiceNo 있고 shippedAt 없음
        }

        @Test
        @DisplayName("getOrders() — 빈 목록을 반환할 경우 빈 리스트가 반환된다")
        void getOrders_returnsEmptyList_whenNoOrders() {
            // given
            given(channelOrderMapper.findBySellerIdWithItemSummary("seller-B"))
                    .willReturn(List.of());

            // when
            List<SellerChannelOrderDto> result = sellerChannelOrderQueryService.getOrders("seller-B");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getOrders() — sellerId가 null이면 IllegalArgumentException을 던진다")
        void getOrders_throwsWhenSellerIdIsNull() {
            assertThatThrownBy(() -> sellerChannelOrderQueryService.getOrders(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sellerId는 필수");
        }

        @Test
        @DisplayName("getOrders() — sellerId가 빈 문자열이면 IllegalArgumentException을 던진다")
        void getOrders_throwsWhenSellerIdIsBlank() {
            assertThatThrownBy(() -> sellerChannelOrderQueryService.getOrders("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sellerId는 필수");
        }

        @Test
        @DisplayName("buildItemsSummary() — itemCount가 1이면 첫 번째 상품명만 반환한다")
        void buildItemsSummary_singleItem_returnsNameOnly() {
            // package-private 메서드 직접 호출 (동일 패키지)
            String result = sellerChannelOrderQueryService.buildItemsSummary("상품A", 1);
            assertThat(result).isEqualTo("상품A");
        }

        @Test
        @DisplayName("buildItemsSummary() — itemCount가 여러 건이면 '이름 외 N건' 형식으로 반환한다")
        void buildItemsSummary_multipleItems_returnsSummary() {
            String result = sellerChannelOrderQueryService.buildItemsSummary("상품A", 4);
            assertThat(result).isEqualTo("상품A 외 3건");
        }

        @Test
        @DisplayName("buildItemsSummary() — firstItemName이 null이면 빈 문자열을 반환한다")
        void buildItemsSummary_nullName_returnsEmpty() {
            String result = sellerChannelOrderQueryService.buildItemsSummary(null, 3);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolveStatus() — shippedAt이 있으면 SHIPPED를 반환한다")
        void resolveStatus_returnsShipped_whenShippedAtExists() {
            assertThat(sellerChannelOrderQueryService.resolveStatus("INV-001", "2024-01-20"))
                    .isEqualTo("SHIPPED");
        }

        @Test
        @DisplayName("resolveStatus() — invoiceNo만 있으면 PROCESSING을 반환한다")
        void resolveStatus_returnsProcessing_whenOnlyInvoiceExists() {
            assertThat(sellerChannelOrderQueryService.resolveStatus("INV-001", null))
                    .isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("resolveStatus() — 모두 없으면 NEW를 반환한다")
        void resolveStatus_returnsNew_whenBothNull() {
            assertThat(sellerChannelOrderQueryService.resolveStatus(null, null))
                    .isEqualTo("NEW");
        }

        @Test
        @DisplayName("resolveStatus() — shippedAt이 빈 문자열이면 invoiceNo 존재 여부로 판단한다")
        void resolveStatus_blankShippedAt_usesInvoiceNo() {
            // shippedAt이 blank이면 SHIPPED가 아니고, invoiceNo로 판단
            assertThat(sellerChannelOrderQueryService.resolveStatus("INV-001", ""))
                    .isEqualTo("PROCESSING");
        }
    }

    /* ===================================================================
     * SellerChannelCardQueryService
     * =================================================================== */

    @Nested
    @DisplayName("SellerChannelCardQueryService")
    class SellerChannelCardQueryServiceTests {

        @Mock
        private SellerChannelCardMapper channelCardMapper;

        @InjectMocks
        private SellerChannelCardQueryService sellerChannelCardQueryService;

        @Test
        @DisplayName("getChannelCards() — Mapper 결과에 label이 후처리되어 반환된다")
        void getChannelCards_setsLabel() {
            // given
            SellerChannelCardDto card = new SellerChannelCardDto();
            card.setKey("SHOPIFY");
            card.setPendingOrders(5);

            given(channelCardMapper.findBySellerIdGroupedByChannel("seller-A"))
                    .willReturn(List.of(card));

            // when
            List<SellerChannelCardDto> result = sellerChannelCardQueryService.getChannelCards("seller-A");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLabel()).isEqualTo("Shopify"); // SHOPIFY → Shopify
        }

        @Test
        @DisplayName("getChannelCards() — 여러 채널 카드가 있으면 각각 label이 변환된다")
        void getChannelCards_multiplCards_allLabeled() {
            // given
            SellerChannelCardDto shopify = new SellerChannelCardDto();
            shopify.setKey("SHOPIFY");

            SellerChannelCardDto amazon = new SellerChannelCardDto();
            amazon.setKey("AMAZON");

            given(channelCardMapper.findBySellerIdGroupedByChannel("seller-B"))
                    .willReturn(List.of(shopify, amazon));

            // when
            List<SellerChannelCardDto> result = sellerChannelCardQueryService.getChannelCards("seller-B");

            // then
            assertThat(result).extracting(SellerChannelCardDto::getLabel)
                    .containsExactly("Shopify", "Amazon");
        }

        @Test
        @DisplayName("getChannelCards() — sellerId가 null이면 IllegalArgumentException을 던진다")
        void getChannelCards_throwsWhenSellerIdIsNull() {
            assertThatThrownBy(() -> sellerChannelCardQueryService.getChannelCards(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sellerId는 필수");
        }

        @Test
        @DisplayName("getChannelCards() — sellerId가 빈 문자열이면 IllegalArgumentException을 던진다")
        void getChannelCards_throwsWhenSellerIdIsBlank() {
            assertThatThrownBy(() -> sellerChannelCardQueryService.getChannelCards("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sellerId는 필수");
        }

        @Test
        @DisplayName("toLabel() — 알려진 채널명을 올바른 표시 이름(Shopify, Amazon 등)으로 변환한다")
        void toLabel_mapsKnownChannels() {
            // package-private 메서드 직접 호출 (동일 패키지)
            assertThat(sellerChannelCardQueryService.toLabel("SHOPIFY")).isEqualTo("Shopify");
            assertThat(sellerChannelCardQueryService.toLabel("AMAZON")).isEqualTo("Amazon");
            assertThat(sellerChannelCardQueryService.toLabel("MANUAL")).isEqualTo("Manual");
            assertThat(sellerChannelCardQueryService.toLabel("EXCEL")).isEqualTo("Excel");
        }

        @Test
        @DisplayName("toLabel() — 알 수 없는 채널명이면 원래 문자열 그대로 반환한다")
        void toLabel_returnsOriginalForUnknown() {
            assertThat(sellerChannelCardQueryService.toLabel("TIKTOK")).isEqualTo("TIKTOK");
        }

        @Test
        @DisplayName("toLabel() — null이면 빈 문자열을 반환한다")
        void toLabel_returnsEmpty_whenNull() {
            assertThat(sellerChannelCardQueryService.toLabel(null)).isEmpty();
        }
    }
}
