package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.response.BulkFulfillmentResponse;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FulfillmentCommandController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("FulfillmentCommandController 테스트")
class FulfillmentCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChannelFulfillmentDispatchService fulfillmentDispatchService;

    @Nested
    @DisplayName("주문 fulfillment 생성 테스트")
    class CreateSellerOrderFulfillmentTests {

        @Test
        @DisplayName("유효한 주문 번호가 주어지면 fulfillment 생성을 요청했을 때 HTTP 200 응답을 반환해야 한다")
        void createSellerOrderFulfillment_returnsOk() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/fulfillment/{orderId}", "ORD-20260330-0001"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"success":true,"data":null}
                            """));

            then(fulfillmentDispatchService).should()
                    .fulfill("ORD-20260330-0001");
        }

        @Test
        @DisplayName("존재하지 않는 주문 번호가 주어지면 fulfillment 생성을 요청했을 때 HTTP 404 응답을 반환해야 한다")
        void createSellerOrderFulfillment_orderNotFound_returns404() throws Exception {
            doThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다: ORD-404"))
                    .when(fulfillmentDispatchService)
                    .fulfill("ORD-404");

            mockMvc.perform(post("/integrations/seller/orders/fulfillment/{orderId}", "ORD-404"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-101"))
                    .andExpect(jsonPath("$.message").value("주문을 찾을 수 없습니다: ORD-404"));
        }

        @Test
        @DisplayName("송장이 발급되지 않은 주문 번호가 주어지면 fulfillment 생성을 요청했을 때 HTTP 409 응답을 반환해야 한다")
        void createSellerOrderFulfillment_orderNotInvoiced_returns409() throws Exception {
            doThrow(new BusinessException(ErrorCode.ORDER_NOT_INVOICED, "송장이 발급되지 않은 주문입니다: ORD-20260330-0001"))
                    .when(fulfillmentDispatchService)
                    .fulfill("ORD-20260330-0001");

            mockMvc.perform(post("/integrations/seller/orders/fulfillment/{orderId}", "ORD-20260330-0001"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-202"))
                    .andExpect(jsonPath("$.message").value("송장이 발급되지 않은 주문입니다: ORD-20260330-0001"));
        }

        @Test
        @DisplayName("GET 메서드로 fulfillment 생성을 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void createSellerOrderFulfillment_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/fulfillment/{orderId}", "ORD-20260330-0001"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("일괄 fulfillment 전송 테스트")
    class CreateBulkFulfillmentTests {

        private static final String REQUEST_BODY = """
                {"orderChannel":"SHOPIFY"}
                """;

        @Test
        @DisplayName("유효한 판매자 헤더와 채널 정보가 주어지면 일괄 fulfillment 전송을 요청했을 때 HTTP 200 응답과 처리 건수를 반환해야 한다")
        void createBulkFulfillment_returnsOk() throws Exception {
            given(fulfillmentDispatchService.fulfillBulk(anyString(), any()))
                    .willReturn(new BulkFulfillmentResponse(2, 1));

            mockMvc.perform(post("/integrations/seller/orders/bulk-fulfillment")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.successCount").value(2))
                    .andExpect(jsonPath("$.data.failCount").value(1));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 일괄 fulfillment 전송을 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void createBulkFulfillment_missingSellerIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/bulk-fulfillment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: X-Seller-Id"));
        }

        @Test
        @DisplayName("지원하지 않는 채널 정보가 주어지면 일괄 fulfillment 전송을 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void createBulkFulfillment_unsupportedChannel_returns400() throws Exception {
            given(fulfillmentDispatchService.fulfillBulk(anyString(), any()))
                    .willThrow(new BusinessException(ErrorCode.UNSUPPORTED_CHANNEL, "지원하지 않는 fulfillment 채널입니다."));

            mockMvc.perform(post("/integrations/seller/orders/bulk-fulfillment")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-004"))
                    .andExpect(jsonPath("$.message").value("지원하지 않는 fulfillment 채널입니다."));
        }

        @Test
        @DisplayName("서비스에서 예기치 않은 예외가 발생하면 일괄 fulfillment 전송을 요청했을 때 HTTP 500 응답을 반환해야 한다")
        void createBulkFulfillment_serviceThrows_returns500() throws Exception {
            given(fulfillmentDispatchService.fulfillBulk(anyString(), any()))
                    .willThrow(new RuntimeException("예상치 못한 서버 오류"));

            mockMvc.perform(post("/integrations/seller/orders/bulk-fulfillment")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("GET 메서드로 일괄 fulfillment 전송을 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void createBulkFulfillment_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/bulk-fulfillment")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
