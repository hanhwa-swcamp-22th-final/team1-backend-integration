package com.conk.integration.query.controller;

import com.conk.integration.query.controller.IntegrationQueryController;
import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.query.dto.SellerChannelOrderDto;
import com.conk.integration.query.service.SellerChannelCardQueryService;
import com.conk.integration.query.service.SellerChannelOrderQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * [4단계] Controller 슬라이스 테스트 — @WebMvcTest + MockMvc
 *
 * - @WebMvcTest : 웹 계층(Controller, Filter, Interceptor 등)만 로드합니다.
 *   JPA / DB / Service 등은 로드되지 않습니다.
 * - @MockitoBean : Service 계층을 가짜(MockitoBean)로 대체해 웹 통신 자체에만 집중합니다.
 * - 검증 항목: HTTP 상태 코드, 응답 JSON 구조, 헤더 처리 여부
 */
@WebMvcTest(IntegrationQueryController.class)
@DisplayName("[Controller] IntegrationQueryController 슬라이스 테스트")
class IntegrationQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerChannelCardQueryService channelCardQueryService;

    @MockitoBean
    private SellerChannelOrderQueryService channelOrderQueryService;

    /* ===================================================================
     * GET /integrations/seller/channels  (INT-001)
     * =================================================================== */

    @Nested
    @DisplayName("GET /integrations/seller/channels — 채널 카드 조회 (INT-001)")
    class GetSellerChannelCardsTests {

        // 헤더 입력과 응답 JSON 구조가 계약대로 유지되는지 확인한다.
        @Test
        @DisplayName("정상 요청 — HTTP 200과 success:true, 채널 카드 목록이 반환된다")
        void getSellerChannelCards_returnsOk() throws Exception {
            // given
            SellerChannelCardDto card = new SellerChannelCardDto();
            card.setKey("SHOPIFY");
            card.setLabel("Shopify");
            card.setSyncStatus("OK");
            card.setPendingOrders(3);
            card.setTodayImported(10);
            card.setLastSyncedAt(LocalDateTime.of(2024, 1, 15, 9, 0));

            given(channelCardQueryService.getChannelCards("seller-A"))
                    .willReturn(List.of(card));

            // when & then
            mockMvc.perform(get("/integrations/seller/channels")
                            .header("X-Seller-Id", "seller-A"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].key").value("SHOPIFY"))
                    .andExpect(jsonPath("$.data[0].label").value("Shopify"))
                    .andExpect(jsonPath("$.data[0].pendingOrders").value(3));
        }

        @Test
        @DisplayName("정상 요청 — 채널 카드가 없을 때 빈 배열이 반환된다")
        void getSellerChannelCards_returnsEmptyList() throws Exception {
            // given
            given(channelCardQueryService.getChannelCards("seller-B")).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/integrations/seller/channels")
                            .header("X-Seller-Id", "seller-B"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("X-Seller-Id 헤더가 없으면 HTTP 400이 반환된다")
        void getSellerChannelCards_missingHeader_returns400() throws Exception {
            // when & then — 요청 헤더 없이 호출
            mockMvc.perform(get("/integrations/seller/channels"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Service가 IllegalArgumentException 던지면 HTTP 400이 반환된다")
        void getSellerChannelCards_serviceThrowsIllegalArg_returns400() throws Exception {
            // given
            given(channelCardQueryService.getChannelCards(""))
                    .willThrow(new IllegalArgumentException("sellerId는 필수입니다."));

            // when & then — GlobalExceptionHandler에 의해 400 변환
            mockMvc.perform(get("/integrations/seller/channels")
                            .header("X-Seller-Id", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("sellerId는 필수입니다."));
        }

        @Test
        @DisplayName("여러 채널 카드가 있을 때 모두 반환된다")
        void getSellerChannelCards_multipleCards() throws Exception {
            // 배열 응답에서 다건 직렬화가 정상 동작하는지 본다.
            // given
            SellerChannelCardDto shopify = new SellerChannelCardDto();
            shopify.setKey("SHOPIFY"); shopify.setLabel("Shopify"); shopify.setPendingOrders(5);

            SellerChannelCardDto amazon = new SellerChannelCardDto();
            amazon.setKey("AMAZON"); amazon.setLabel("Amazon"); amazon.setPendingOrders(2);

            given(channelCardQueryService.getChannelCards("seller-multi"))
                    .willReturn(List.of(shopify, amazon));

            // when & then
            mockMvc.perform(get("/integrations/seller/channels")
                            .header("X-Seller-Id", "seller-multi"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].key").value("SHOPIFY"))
                    .andExpect(jsonPath("$.data[1].key").value("AMAZON"));
        }
    }

    /* ===================================================================
     * GET /integrations/seller/orders  (INT-002)
     * =================================================================== */

    @Nested
    @DisplayName("GET /integrations/seller/orders — 통합 주문 조회 (INT-002)")
    class GetSellerChannelOrdersTests {

        // 주문 조회 응답의 대표 필드가 JSON으로 정확히 직렬화되는지 본다.
        @Test
        @DisplayName("정상 요청 — HTTP 200과 주문 목록이 반환된다")
        void getSellerChannelOrders_returnsOk() throws Exception {
            // given
            SellerChannelOrderDto order = SellerChannelOrderDto.builder()
                    .id("O-001")
                    .channel("SHOPIFY")
                    .channelOrderNo("#1001")
                    .conkOrderNo("O-001")
                    .recipient("홍길동")
                    .itemsSummary("상품A 외 2건")
                    .orderAmount(null)
                    .orderedAt(LocalDateTime.of(2024, 1, 15, 10, 0))
                    .status("NEW")
                    .build();

            given(channelOrderQueryService.getOrders("seller-A")).willReturn(List.of(order));

            // when & then
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-A"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id").value("O-001"))
                    .andExpect(jsonPath("$.data[0].channel").value("SHOPIFY"))
                    .andExpect(jsonPath("$.data[0].recipient").value("홍길동"))
                    .andExpect(jsonPath("$.data[0].itemsSummary").value("상품A 외 2건"))
                    .andExpect(jsonPath("$.data[0].status").value("NEW"));
        }

        @Test
        @DisplayName("정상 요청 — 주문이 없을 때 빈 배열이 반환된다")
        void getSellerChannelOrders_returnsEmptyList() throws Exception {
            // given
            given(channelOrderQueryService.getOrders("seller-B")).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-B"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("X-Seller-Id 헤더가 없으면 HTTP 400이 반환된다")
        void getSellerChannelOrders_missingHeader_returns400() throws Exception {
            // when & then
            mockMvc.perform(get("/integrations/seller/orders"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PROCESSING 상태의 주문도 올바르게 직렬화된다")
        void getSellerChannelOrders_processingStatus() throws Exception {
            // given
            SellerChannelOrderDto processingOrder = SellerChannelOrderDto.builder()
                    .id("O-002")
                    .channel("SHOPIFY")
                    .channelOrderNo("#1002")
                    .conkOrderNo("O-002")
                    .recipient("김철수")
                    .itemsSummary("상품B")
                    .orderedAt(LocalDateTime.of(2024, 1, 16, 11, 0))
                    .status("PROCESSING")
                    .build();

            given(channelOrderQueryService.getOrders("seller-C")).willReturn(List.of(processingOrder));

            // when & then
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-C"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value("PROCESSING"));
        }

        @Test
        @DisplayName("응답의 최상위 success 필드가 항상 true이다")
        void getSellerChannelOrders_successFieldIsTrue() throws Exception {
            // given
            given(channelOrderQueryService.getOrders("seller-D")).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-D"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
