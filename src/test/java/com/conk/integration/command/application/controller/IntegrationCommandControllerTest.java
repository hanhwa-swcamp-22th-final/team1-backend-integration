package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.controller.IntegrationCommandController;
import com.conk.integration.command.application.dto.response.BulkInvoiceResponse;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
import com.conk.integration.command.application.service.EasyPostInvoiceSaveService;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
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
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.given;
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

    @MockitoBean
    private EasyPostInvoiceSaveService easyPostInvoiceSaveService;

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

    @Nested
    @DisplayName("POST /integrations/seller/orders/invoice — EasyPost 단건 송장 발급 (INT-005)")
    class CreateShipmentInvoiceTests {

        private static final String REQUEST_BODY = """
                {"shipment":{"to_address":{"name":"John","street1":"417 Montgomery St","city":"San Francisco","state":"CA","zip":"94104","country":"US"},"from_address":{"name":"EasyPost","street1":"417 Montgomery St","city":"San Francisco","state":"CA","zip":"94104","country":"US"},"parcel":{"weight":21.9,"length":10.0,"width":8.0,"height":4.0}}}
                """;

        @Test
        @DisplayName("정상 요청 — HTTP 200과 success:true, data(송장 정보)가 반환된다")
        void createShipmentInvoice_returnsOk() throws Exception {
            EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                    .invoiceNo("shp_test_001")
                    .carrierType(CarrierType.USPS)
                    .freightChargeAmt(550)
                    .shipToAddress("417 Montgomery St, San Francisco, CA, 94104, US")
                    .trackingUrl("https://track.easypost.com/TRK001")
                    .labelFileUrl("https://easypost.com/labels/shp_test_001.pdf")
                    .build();

            given(easyPostInvoiceSaveService.createAndSaveInvoice(any())).willReturn(invoice);

            mockMvc.perform(post("/integrations/seller/orders/invoice")
                            .header("Authorization", "Bearer test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.invoiceNo").value("shp_test_001"))
                    .andExpect(jsonPath("$.data.carrierType").value("USPS"))
                    .andExpect(jsonPath("$.data.freightChargeAmt").value(550))
                    .andExpect(jsonPath("$.data.trackingUrl").value("https://track.easypost.com/TRK001"))
                    .andExpect(jsonPath("$.data.labelFileUrl").value("https://easypost.com/labels/shp_test_001.pdf"));
        }

        @Test
        @DisplayName("Authorization 헤더가 없으면 HTTP 400이 반환된다")
        void createShipmentInvoice_missingAuthorization_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/invoice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: Authorization"));
        }

        @Test
        @DisplayName("Service가 IllegalStateException을 던지면 HTTP 400이 반환된다")
        void createShipmentInvoice_illegalState_returns400() throws Exception {
            given(easyPostInvoiceSaveService.createAndSaveInvoice(any()))
                    .willThrow(new IllegalStateException("운임 정보가 없습니다"));

            mockMvc.perform(post("/integrations/seller/orders/invoice")
                            .header("Authorization", "Bearer test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("운임 정보가 없습니다"));
        }

        @Test
        @DisplayName("POST 외 메서드로 호출하면 HTTP 405가 반환된다")
        void createShipmentInvoice_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/invoice")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("POST /integrations/seller/orders/bulk-invoice — EasyPost 일괄 송장 발급 (INT-006)")
    class CreateBulkShipmentInvoiceTests {

        private static final String REQUEST_BODY = """
                {"sellerId":"seller-001","fromAddress":{"name":"EasyPost","street1":"417 Montgomery St","city":"San Francisco","state":"CA","zip":"94104","country":"US"},"parcel":{"weight":21.9,"length":10.0,"width":8.0,"height":4.0}}
                """;

        @Test
        @DisplayName("정상 요청 — HTTP 200과 success:true, data(successCount/failCount)가 반환된다")
        void createBulkShipmentInvoice_returnsOk() throws Exception {
            given(easyPostInvoiceSaveService.createAndSaveBulkInvoices(any(), any(), any()))
                    .willReturn(new BulkInvoiceResponse(2, 0));

            mockMvc.perform(post("/integrations/seller/orders/bulk-invoice")
                            .header("Authorization", "Bearer test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.successCount").value(2))
                    .andExpect(jsonPath("$.data.failCount").value(0));
        }

        @Test
        @DisplayName("Authorization 헤더가 없으면 HTTP 400이 반환된다")
        void createBulkShipmentInvoice_missingAuthorization_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/bulk-invoice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: Authorization"));
        }

        @Test
        @DisplayName("Service가 IllegalStateException을 던지면 HTTP 400이 반환된다")
        void createBulkShipmentInvoice_serviceThrows_returns400() throws Exception {
            given(easyPostInvoiceSaveService.createAndSaveBulkInvoices(any(), any(), any()))
                    .willThrow(new IllegalStateException("DB 연결 오류"));

            mockMvc.perform(post("/integrations/seller/orders/bulk-invoice")
                            .header("Authorization", "Bearer test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("DB 연결 오류"));
        }

        @Test
        @DisplayName("POST 외 메서드로 호출하면 HTTP 405가 반환된다")
        void createBulkShipmentInvoice_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/bulk-invoice")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
