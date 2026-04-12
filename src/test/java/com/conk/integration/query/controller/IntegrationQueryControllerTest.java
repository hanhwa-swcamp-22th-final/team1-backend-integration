package com.conk.integration.query.controller;

import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.controller.IntegrationQueryController;
import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.common.channel.dto.SellerChannelDetailDto;
import com.conk.integration.query.dto.SellerChannelOrderDto;
import com.conk.integration.query.service.SellerChannelCardQueryService;
import com.conk.integration.query.service.SellerChannelDetailQueryService;
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
@DisplayName("IntegrationQueryController 테스트")
class IntegrationQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerChannelCardQueryService channelCardQueryService;

    @MockitoBean
    private SellerChannelDetailQueryService channelDetailQueryService;

    @MockitoBean
    private SellerChannelOrderQueryService channelOrderQueryService;

    /* ===================================================================
     * GET /integrations/seller/channels  (INT-001)
     * =================================================================== */

    @Nested
    @DisplayName("채널 카드 조회 테스트")
    class GetSellerChannelCardsTests {

        // 헤더 입력과 응답 JSON 구조가 계약대로 유지되는지 확인한다.
        @Test
        @DisplayName("유효한 판매자 헤더가 주어지면 채널 카드 조회를 요청했을 때 HTTP 200 응답과 채널 카드 목록을 반환해야 한다")
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
        @DisplayName("조회 결과가 없으면 채널 카드 조회를 요청했을 때 HTTP 200 응답과 빈 배열을 반환해야 한다")
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
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 채널 카드 조회를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void getSellerChannelCards_missingHeader_returns400() throws Exception {
            // when & then — 요청 헤더 없이 호출
            mockMvc.perform(get("/integrations/seller/channels"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("서비스에서 BusinessException(INT-001)이 발생하면 채널 카드 조회를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void getSellerChannelCards_serviceThrowsIllegalArg_returns400() throws Exception {
            // given
            given(channelCardQueryService.getChannelCards(""))
                    .willThrow(new BusinessException(ErrorCode.INVALID_SELLER_ID));

            // when & then — GlobalExceptionHandler에 의해 400 변환
            mockMvc.perform(get("/integrations/seller/channels")
                            .header("X-Seller-Id", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-001"))
                    .andExpect(jsonPath("$.message").value("sellerId는 필수입니다."));
        }

        @Test
        @DisplayName("여러 채널 카드가 있으면 채널 카드 조회를 요청했을 때 모든 채널 카드를 반환해야 한다")
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
     * GET /integrations/seller/channels/{channelKey}
     * =================================================================== */

    @Nested
    @DisplayName("채널 연결 상세 조회 테스트")
    class GetSellerChannelDetailTests {

        @Test
        @DisplayName("유효한 판매자 헤더와 채널 키가 주어지면 채널 연결 상세 조회를 요청했을 때 HTTP 200 응답과 상세 정보를 반환해야 한다")
        void getSellerChannelDetail_returnsOk() throws Exception {
            SellerChannelDetailDto detail = new SellerChannelDetailDto(
                    "SHOPIFY",
                    "my-shopify-store",
                    "shpat_xxxxxxxx",
                    LocalDateTime.of(2026, 1, 15, 9, 0));

            given(channelDetailQueryService.getChannelDetail("seller-A", "SHOPIFY"))
                    .willReturn(detail);

            mockMvc.perform(get("/integrations/seller/channels/SHOPIFY")
                            .header("X-Seller-Id", "seller-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.channelName").value("SHOPIFY"))
                    .andExpect(jsonPath("$.data.storeName").value("my-shopify-store"))
                    .andExpect(jsonPath("$.data.channelApi").value("shpat_xxxxxxxx"));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 채널 연결 상세 조회를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void getSellerChannelDetail_missingHeader_returns400() throws Exception {
            mockMvc.perform(get("/integrations/seller/channels/SHOPIFY"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("연결 정보가 없으면 채널 연결 상세 조회를 요청했을 때 HTTP 404 응답을 반환해야 한다")
        void getSellerChannelDetail_notFound_returns404() throws Exception {
            given(channelDetailQueryService.getChannelDetail("seller-A", "SHOPIFY"))
                    .willThrow(new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND));

            mockMvc.perform(get("/integrations/seller/channels/SHOPIFY")
                            .header("X-Seller-Id", "seller-A"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-404"))
                    .andExpect(jsonPath("$.message").value("연결된 채널 정보를 찾을 수 없습니다."));
        }
    }

    /* ===================================================================
     * GET /integrations/seller/orders  (INT-002)
     * =================================================================== */

    @Nested
    @DisplayName("통합 주문 조회 테스트")
    class GetSellerChannelOrdersTests {

        // 주문 조회 응답의 대표 필드가 JSON으로 정확히 직렬화되는지 본다.
        @Test
        @DisplayName("유효한 판매자 헤더가 주어지면 통합 주문 조회를 요청했을 때 HTTP 200 응답과 주문 목록을 반환해야 한다")
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
        @DisplayName("주문이 없으면 통합 주문 조회를 요청했을 때 HTTP 200 응답과 빈 배열을 반환해야 한다")
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
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 통합 주문 조회를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void getSellerChannelOrders_missingHeader_returns400() throws Exception {
            // when & then
            mockMvc.perform(get("/integrations/seller/orders"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PROCESSING 상태의 주문이 주어지면 통합 주문 조회를 요청했을 때 상태 값을 올바르게 직렬화해야 한다")
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
        @DisplayName("정상 요청이 주어지면 통합 주문 조회를 요청했을 때 응답 최상위 success 필드는 true여야 한다")
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

