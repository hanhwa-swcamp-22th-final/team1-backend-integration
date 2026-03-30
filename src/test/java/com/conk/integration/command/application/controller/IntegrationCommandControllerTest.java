package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.controller.IntegrationCommandController;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
import com.conk.integration.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// fulfillment command API의 웹 계약을 슬라이스 테스트로 고정한다.
@WebMvcTest(IntegrationCommandController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("[Controller] IntegrationCommandController 슬라이스 테스트")
class IntegrationCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChannelFulfillmentDispatchService fulfillmentDispatchService;

    @Nested
    @DisplayName("POST /integrations/seller/orders/fulfillment/{orderId} — fulfillment 생성 (INT-003)")
    class CreateSellerOrderFulfillmentTests {

        @Test
        @DisplayName("정상 요청 — HTTP 200과 success:true, data:null 이 반환된다")
        void createSellerOrderFulfillment_returnsOk() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/fulfillment/{orderId}", "ORD-20260330-0001")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"success":true,"data":null}
                            """));

            then(fulfillmentDispatchService).should()
                    .fulfill("ORD-20260330-0001");
        }

        @Test
        @DisplayName("Authorization 헤더가 없으면 HTTP 400이 반환된다")
        void createSellerOrderFulfillment_missingAuthorization_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/fulfillment/{orderId}", "ORD-20260330-0001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: Authorization"));
        }

        @Test
        @DisplayName("Service가 IllegalArgumentException을 던지면 HTTP 400이 반환된다")
        void createSellerOrderFulfillment_illegalArgument_returns400() throws Exception {
            doThrow(new IllegalArgumentException("ChannelOrder를 찾을 수 없습니다: ORD-404"))
                    .when(fulfillmentDispatchService)
                    .fulfill("ORD-404");

            mockMvc.perform(post("/integrations/seller/orders/fulfillment/{orderId}", "ORD-404")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("ChannelOrder를 찾을 수 없습니다: ORD-404"));
        }

        @Test
        @DisplayName("Service가 IllegalStateException을 던지면 HTTP 400이 반환된다")
        void createSellerOrderFulfillment_illegalState_returns400() throws Exception {
            doThrow(new IllegalStateException("송장이 발급되지 않은 주문입니다: ORD-20260330-0001"))
                    .when(fulfillmentDispatchService)
                    .fulfill("ORD-20260330-0001");

            mockMvc.perform(post("/integrations/seller/orders/fulfillment/{orderId}", "ORD-20260330-0001")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("송장이 발급되지 않은 주문입니다: ORD-20260330-0001"));
        }

        @Test
        @DisplayName("POST 외 메서드로 호출하면 HTTP 405가 반환된다")
        void createSellerOrderFulfillment_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/fulfillment/{orderId}", "ORD-20260330-0001")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
