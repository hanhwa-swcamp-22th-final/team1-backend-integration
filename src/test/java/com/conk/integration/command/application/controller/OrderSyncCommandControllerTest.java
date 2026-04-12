package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.application.service.ChannelOrderSyncDispatchService;
import com.conk.integration.command.application.service.SellerChannelImportPreviewService;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderSyncCommandController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("OrderSyncCommandController 테스트")
class OrderSyncCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChannelOrderSyncDispatchService orderSyncDispatchService;

    @MockitoBean
    private SellerChannelImportPreviewService sellerChannelImportPreviewService;

    @Nested
    @DisplayName("채널 주문 동기화 테스트")
    class SyncChannelOrdersTests {

        private static final String REQUEST_BODY = """
                {"orderChannel":"SHOPIFY"}
                """;

        @Test
        @DisplayName("유효한 판매자 헤더와 채널 정보가 주어지면 채널 주문 동기화를 요청했을 때 HTTP 200 응답과 저장 결과를 반환해야 한다")
        void syncChannelOrders_validRequest_returns200() throws Exception {
            ChannelOrderSyncResponse response = new ChannelOrderSyncResponse(3, 1, List.of());
            given(orderSyncDispatchService.sync(any(), any())).willReturn(response);

            mockMvc.perform(post("/integrations/seller/orders/sync")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.savedCount").value(3))
                    .andExpect(jsonPath("$.data.skippedCount").value(1));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 채널 주문 동기화를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void syncChannelOrders_missingSellerIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/sync")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: X-Seller-Id"));
        }

        @Test
        @DisplayName("서비스에서 BusinessException(INT-004)이 발생하면 채널 주문 동기화를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void syncChannelOrders_serviceThrows_returns400() throws Exception {
            given(orderSyncDispatchService.sync(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.UNSUPPORTED_CHANNEL, "지원하지 않는 채널"));

            mockMvc.perform(post("/integrations/seller/orders/sync")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-004"))
                    .andExpect(jsonPath("$.message").value("지원하지 않는 채널"));
        }

        @Test
        @DisplayName("GET 메서드로 채널 주문 동기화를 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void syncChannelOrders_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/sync")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("유효한 판매자 헤더와 채널 키가 주어지면 채널 기준 주문 동기화를 요청했을 때 HTTP 200 응답과 저장 결과를 반환해야 한다")
        void syncChannelOrdersByChannel_validRequest_returns200() throws Exception {
            ChannelOrderSyncResponse response = new ChannelOrderSyncResponse(2, 1, List.of());
            given(orderSyncDispatchService.sync(any(), any())).willReturn(response);

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/sync", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.savedCount").value(2))
                    .andExpect(jsonPath("$.data.skippedCount").value(1));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 채널 기준 주문 동기화를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void syncChannelOrdersByChannel_missingSellerIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/sync", "SHOPIFY"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: X-Seller-Id"));
        }

        @Test
        @DisplayName("지원하지 않는 채널 키가 주어지면 채널 기준 주문 동기화를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void syncChannelOrdersByChannel_unsupportedChannel_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/sync", "UNKNOWN")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-004"))
                    .andExpect(jsonPath("$.message").value("지원하지 않는 주문 동기화 채널입니다: UNKNOWN"));
        }

        @Test
        @DisplayName("GET 메서드로 채널 기준 주문 동기화를 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void syncChannelOrdersByChannel_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/channels/{channelKey}/sync", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("채널 주문 가져오기 테스트")
    class ImportChannelOrdersTests {

        @Test
        @DisplayName("요청 본문이 없는 요청이 주어지면 채널 주문 가져오기를 요청했을 때 HTTP 200 응답과 처리 건수를 반환해야 한다")
        void importChannelOrders_withoutBody_returns200() throws Exception {
            ChannelOrderSyncResponse response = new ChannelOrderSyncResponse(10, 2, List.of());
            given(orderSyncDispatchService.sync(any(), any())).willReturn(response);

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-orders", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.importedCount").value(10))
                    .andExpect(jsonPath("$.data.skippedCount").value(2));
        }

        @Test
        @DisplayName("빈 JSON 본문이 주어지면 채널 주문 가져오기를 요청했을 때 HTTP 200 응답과 처리 건수를 반환해야 한다")
        void importChannelOrders_emptyJsonBody_returns200() throws Exception {
            ChannelOrderSyncResponse response = new ChannelOrderSyncResponse(3, 1, List.of());
            given(orderSyncDispatchService.sync(any(), any())).willReturn(response);

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-orders", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.importedCount").value(3))
                    .andExpect(jsonPath("$.data.skippedCount").value(1));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 채널 주문 가져오기를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void importChannelOrders_missingSellerIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-orders", "SHOPIFY"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: X-Seller-Id"));
        }

        @Test
        @DisplayName("지원하지 않는 채널 키가 주어지면 채널 주문 가져오기를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void importChannelOrders_unsupportedChannel_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-orders", "UNKNOWN")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-004"))
                    .andExpect(jsonPath("$.message").value("지원하지 않는 주문 동기화 채널입니다: UNKNOWN"));
        }

        @Test
        @DisplayName("서비스에서 지원하지 않는 채널 예외가 발생하면 채널 주문 가져오기를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void importChannelOrders_serviceThrows_returns400() throws Exception {
            given(orderSyncDispatchService.sync(any(), any()))
                    .willThrow(new BusinessException(ErrorCode.UNSUPPORTED_CHANNEL, "지원하지 않는 주문 동기화 채널입니다: SHOPIFY"));

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-orders", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-004"))
                    .andExpect(jsonPath("$.message").value("지원하지 않는 주문 동기화 채널입니다: SHOPIFY"));
        }

        @Test
        @DisplayName("GET 메서드로 채널 주문 가져오기를 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void importChannelOrders_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/channels/{channelKey}/import-orders", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("채널 주문 가져오기 미리보기 테스트")
    class ImportChannelPreviewTests {

        private static final String REQUEST_BODY = """
                {
                  "storeAlias": "Shopify KR Store",
                  "contactEmail": "ops@example.com",
                  "syncWindow": "최근 7일",
                  "autoImport": true
                }
                """;

        @Test
        @DisplayName("유효한 판매자 헤더와 요청 본문이 주어지면 채널 주문 가져오기 미리보기를 요청했을 때 HTTP 200 응답과 미리보기 정보를 반환해야 한다")
        void importChannelPreview_returns200() throws Exception {
            SellerChannelImportPreviewResponse response = new SellerChannelImportPreviewResponse(
                    5,
                    java.time.LocalDateTime.of(2026, 4, 11, 9, 0));
            given(sellerChannelImportPreviewService.preview(anyString(), any(), any())).willReturn(response);

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-preview", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.pendingOrders").value(5))
                    .andExpect(jsonPath("$.data.lastSyncedAt").value("2026-04-11T09:00:00"));
        }

        @Test
        @DisplayName("요청 본문이 없는 요청이 주어지면 채널 주문 가져오기 미리보기를 요청했을 때 HTTP 200 응답과 미리보기 정보를 반환해야 한다")
        void importChannelPreview_withoutBody_returns200() throws Exception {
            SellerChannelImportPreviewResponse response = new SellerChannelImportPreviewResponse(0, null);
            given(sellerChannelImportPreviewService.preview(anyString(), any(), any())).willReturn(response);

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-preview", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.pendingOrders").value(0))
                    .andExpect(jsonPath("$.data.lastSyncedAt").doesNotExist());
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 채널 주문 가져오기 미리보기를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void importChannelPreview_missingSellerIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-preview", "SHOPIFY")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: X-Seller-Id"));
        }

        @Test
        @DisplayName("지원하지 않는 채널 키가 주어지면 채널 주문 가져오기 미리보기를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void importChannelPreview_unsupportedChannel_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/import-preview", "UNKNOWN")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-004"))
                    .andExpect(jsonPath("$.message").value("지원하지 않는 주문 동기화 채널입니다: UNKNOWN"));
        }

        @Test
        @DisplayName("GET 메서드로 채널 주문 가져오기 미리보기를 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void importChannelPreview_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/channels/{channelKey}/import-preview", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
