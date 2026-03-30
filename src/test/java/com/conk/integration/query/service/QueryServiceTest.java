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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * [3단계] Query Service 단위 테스트 — Mockito
 *
 * - @ExtendWith(MockitoExtension.class) : spring Context 없는 경량 Mockito 환경
 * - MyBatis Mapper를 Mock으로 대체하여 Query Service 로직 자체에 집중
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[Service] Query Service 단위 테스트 (Mockito)")
class QueryServiceTest {

    // 주문 목록 조회는 mapper 결과를 API 응답용 DTO로 후처리하는 책임에 집중한다.
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
            // 요약 문자열, 상태, 표시용 필드 변환이 한 번에 보이도록 대표 케이스를 만든다.
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

            List<SellerChannelOrderDto> result = sellerChannelOrderQueryService.getOrders("seller-A");

            assertThat(result).hasSize(1);
            SellerChannelOrderDto dto = result.get(0);
            assertThat(dto.getId()).isEqualTo("O-001");
            assertThat(dto.getChannel()).isEqualTo("SHOPIFY");
            assertThat(dto.getConkOrderNo()).isEqualTo("O-001");
            assertThat(dto.getItemsSummary()).isEqualTo("상품A 외 2건");
            assertThat(dto.getStatus()).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("getOrders() — 빈 목록을 반환할 경우 빈 리스트가 반환된다")
        void getOrders_returnsEmptyList_whenNoOrders() {
            // mapper가 빈 결과를 줄 때 서비스는 추가 후처리 없이 빈 컬렉션을 유지해야 한다.
            given(channelOrderMapper.findBySellerIdWithItemSummary("seller-B"))
                    .willReturn(List.of());

            List<SellerChannelOrderDto> result = sellerChannelOrderQueryService.getOrders("seller-B");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getOrders() — sellerId가 null이면 IllegalArgumentException을 던진다")
        void getOrders_throwsWhenSellerIdIsNull() {
            // 조회 계층에서도 sellerId 검증을 먼저 수행한다.
            assertThatThrownBy(() -> sellerChannelOrderQueryService.getOrders(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sellerId는 필수");
        }

        @Test
        @DisplayName("getOrders() — sellerId가 빈 문자열이면 IllegalArgumentException을 던진다")
        void getOrders_throwsWhenSellerIdIsBlank() {
            // 공백 문자열은 의미 있는 조회 키가 아니므로 즉시 차단한다.
            assertThatThrownBy(() -> sellerChannelOrderQueryService.getOrders("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sellerId는 필수");
        }

        @Test
        @DisplayName("buildItemsSummary() — itemCount가 1이면 첫 번째 상품명만 반환한다")
        void buildItemsSummary_singleItem_returnsNameOnly() {
            // 단일 품목 주문은 요약 접미사를 붙이지 않는다.
            String result = sellerChannelOrderQueryService.buildItemsSummary("상품A", 1);
            assertThat(result).isEqualTo("상품A");
        }

        @Test
        @DisplayName("buildItemsSummary() — itemCount가 여러 건이면 '이름 외 N건' 형식으로 반환한다")
        void buildItemsSummary_multipleItems_returnsSummary() {
            // 대표 상품명 + 나머지 개수 형식이 리스트 화면 규칙이다.
            String result = sellerChannelOrderQueryService.buildItemsSummary("상품A", 4);
            assertThat(result).isEqualTo("상품A 외 3건");
        }

        @Test
        @DisplayName("buildItemsSummary() — firstItemName이 null이면 빈 문자열을 반환한다")
        void buildItemsSummary_nullName_returnsEmpty() {
            // 대표 상품명이 없으면 깨진 요약 문자열 대신 빈 문자열을 준다.
            String result = sellerChannelOrderQueryService.buildItemsSummary(null, 3);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolveStatus() — shippedAt이 있으면 SHIPPED를 반환한다")
        void resolveStatus_returnsShipped_whenShippedAtExists() {
            // 출고 시각이 존재하면 송장 존재 여부보다 배송 완료 상태가 우선이다.
            assertThat(sellerChannelOrderQueryService.resolveStatus("INV-001", "2024-01-20"))
                    .isEqualTo("SHIPPED");
        }

        @Test
        @DisplayName("resolveStatus() — invoiceNo만 있으면 PROCESSING을 반환한다")
        void resolveStatus_returnsProcessing_whenOnlyInvoiceExists() {
            // 송장은 발급됐지만 shippedAt이 없으면 처리 중으로 본다.
            assertThat(sellerChannelOrderQueryService.resolveStatus("INV-001", null))
                    .isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("resolveStatus() — 모두 없으면 NEW를 반환한다")
        void resolveStatus_returnsNew_whenBothNull() {
            // 송장과 출고 정보가 모두 없으면 신규 주문 상태다.
            assertThat(sellerChannelOrderQueryService.resolveStatus(null, null))
                    .isEqualTo("NEW");
        }

        @Test
        @DisplayName("resolveStatus() — shippedAt이 빈 문자열이면 invoiceNo 존재 여부로 판단한다")
        void resolveStatus_blankShippedAt_usesInvoiceNo() {
            // shippedAt이 blank면 배송 완료로 보지 않고 invoice 존재 여부로 상태를 결정한다.
            assertThat(sellerChannelOrderQueryService.resolveStatus("INV-001", ""))
                    .isEqualTo("PROCESSING");
        }
    }

    // 채널 카드 조회는 mapper 결과에 라벨만 덧입히는 후처리 책임을 검증한다.
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
            // key는 저장용 값이고 label은 화면 표시용 값이라 후처리가 필요하다.
            SellerChannelCardDto card = new SellerChannelCardDto();
            card.setKey("SHOPIFY");
            card.setPendingOrders(5);

            given(channelCardMapper.findBySellerIdGroupedByChannel("seller-A"))
                    .willReturn(List.of(card));

            List<SellerChannelCardDto> result = sellerChannelCardQueryService.getChannelCards("seller-A");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLabel()).isEqualTo("Shopify");
        }

        @Test
        @DisplayName("getChannelCards() — 여러 채널 카드가 있으면 각각 label이 변환된다")
        void getChannelCards_multiplCards_allLabeled() {
            // 채널별 라벨 변환이 항목마다 독립적으로 적용되는지 확인한다.
            SellerChannelCardDto shopify = new SellerChannelCardDto();
            shopify.setKey("SHOPIFY");

            SellerChannelCardDto amazon = new SellerChannelCardDto();
            amazon.setKey("AMAZON");

            given(channelCardMapper.findBySellerIdGroupedByChannel("seller-B"))
                    .willReturn(List.of(shopify, amazon));

            List<SellerChannelCardDto> result = sellerChannelCardQueryService.getChannelCards("seller-B");

            assertThat(result).extracting(SellerChannelCardDto::getLabel)
                    .containsExactly("Shopify", "Amazon");
        }

        @Test
        @DisplayName("getChannelCards() — sellerId가 null이면 IllegalArgumentException을 던진다")
        void getChannelCards_throwsWhenSellerIdIsNull() {
            // 카드 조회도 주문 조회와 동일한 입력 검증 규칙을 따른다.
            assertThatThrownBy(() -> sellerChannelCardQueryService.getChannelCards(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sellerId는 필수");
        }

        @Test
        @DisplayName("getChannelCards() — sellerId가 빈 문자열이면 IllegalArgumentException을 던진다")
        void getChannelCards_throwsWhenSellerIdIsBlank() {
            // 공백 sellerId는 mapper까지 전달하지 않는다.
            assertThatThrownBy(() -> sellerChannelCardQueryService.getChannelCards("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sellerId는 필수");
        }

        @Test
        @DisplayName("toLabel() — 알려진 채널명을 올바른 표시 이름으로 변환한다")
        void toLabel_mapsKnownChannels() {
            // 주요 채널은 사용자가 읽기 쉬운 고정 라벨로 변환한다.
            assertThat(sellerChannelCardQueryService.toLabel("SHOPIFY")).isEqualTo("Shopify");
            assertThat(sellerChannelCardQueryService.toLabel("AMAZON")).isEqualTo("Amazon");
            assertThat(sellerChannelCardQueryService.toLabel("MANUAL")).isEqualTo("Manual");
            assertThat(sellerChannelCardQueryService.toLabel("EXCEL")).isEqualTo("Excel");
        }

        @Test
        @DisplayName("toLabel() — 알 수 없는 채널명이면 원래 문자열 그대로 반환한다")
        void toLabel_returnsOriginalForUnknown() {
            // 미등록 채널은 원본 값을 유지해야 신규 채널 추가 시에도 안전하다.
            assertThat(sellerChannelCardQueryService.toLabel("TIKTOK")).isEqualTo("TIKTOK");
        }

        @Test
        @DisplayName("toLabel() — null이면 빈 문자열을 반환한다")
        void toLabel_returnsEmpty_whenNull() {
            // null key는 화면 표시 단계에서 빈 값으로 정리한다.
            assertThat(sellerChannelCardQueryService.toLabel(null)).isEmpty();
        }
    }
}
