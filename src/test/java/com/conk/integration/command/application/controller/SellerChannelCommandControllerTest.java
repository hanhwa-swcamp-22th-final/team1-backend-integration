package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.service.SellerChannelConnectService;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.common.exception.GlobalExceptionHandler;
import com.conk.integration.common.channel.dto.SellerChannelDetailDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerChannelCommandController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("SellerChannelCommandController 테스트")
class SellerChannelCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerChannelConnectService sellerChannelConnectService;

    @Nested
    @DisplayName("셀러 채널 연결 테스트")
    class ConnectSellerChannelTests {

        private static final String REQUEST_BODY = """
                {
                  "storeName": "my-shopify-store",
                  "channelApi": "shpat_test_token",
                  "storeAlias": "Shopify KR Store",
                  "contactEmail": "ops@example.com",
                  "syncMode": "AUTO"
                }
                """;

        @Test
        @DisplayName("유효한 판매자 헤더와 Shopify 연결 요청이 주어지면 채널 연결을 요청했을 때 HTTP 200 응답과 연결 정보를 반환해야 한다")
        void connectSellerChannel_returnsOk() throws Exception {
            SellerChannelDetailDto response = new SellerChannelDetailDto(
                    "SHOPIFY",
                    "my-shopify-store",
                    "shpat_test_token",
                    java.time.LocalDateTime.of(2026, 4, 10, 10, 30));
            given(sellerChannelConnectService.connect(anyString(), anyString(), any())).willReturn(response);

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/connect", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.channelName").value("SHOPIFY"))
                    .andExpect(jsonPath("$.data.storeName").value("my-shopify-store"))
                    .andExpect(jsonPath("$.data.channelApi").value("shpat_test_token"));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 채널 연결을 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void connectSellerChannel_missingSellerIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/connect", "SHOPIFY")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: X-Seller-Id"));
        }

        @Test
        @DisplayName("지원하지 않는 채널 키가 주어지면 채널 연결을 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void connectSellerChannel_unsupportedChannel_returns400() throws Exception {
            given(sellerChannelConnectService.connect(anyString(), anyString(), any()))
                    .willThrow(new BusinessException(ErrorCode.UNSUPPORTED_CHANNEL,
                            "지원하지 않는 채널 연결 채널입니다: AMAZON"));

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/connect", "AMAZON")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-004"))
                    .andExpect(jsonPath("$.message").value("지원하지 않는 채널 연결 채널입니다: AMAZON"));
        }

        @Test
        @DisplayName("storeName이 없는 요청이 주어지면 채널 연결을 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void connectSellerChannel_missingStoreName_returns400() throws Exception {
            given(sellerChannelConnectService.connect(anyString(), anyString(), any()))
                    .willThrow(new BusinessException(ErrorCode.INVALID_SELLER_ID, "storeName는 필수입니다."));

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/connect", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeName":" ","channelApi":"shpat_test_token"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("storeName는 필수입니다."));
        }

        @Test
        @DisplayName("Shopify 연결 검증에 실패하면 채널 연결을 요청했을 때 HTTP 404 응답을 반환해야 한다")
        void connectSellerChannel_pingFail_returns404() throws Exception {
            given(sellerChannelConnectService.connect(anyString(), anyString(), any()))
                    .willThrow(new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND,
                            "Shopify 채널 연결에 실패했습니다."));

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/connect", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-404"))
                    .andExpect(jsonPath("$.message").value("Shopify 채널 연결에 실패했습니다."));
        }

        @Test
        @DisplayName("GET 메서드로 채널 연결을 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void connectSellerChannel_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/channels/{channelKey}/connect", "SHOPIFY")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
