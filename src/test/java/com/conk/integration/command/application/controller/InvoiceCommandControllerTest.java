package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.response.BulkInvoiceResponse;
import com.conk.integration.command.application.dto.response.ManualOrderInvoiceResponse;
import com.conk.integration.command.application.service.EasyPostInvoiceSaveService;
import com.conk.integration.command.application.service.ManualOrderInvoiceService;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
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

@WebMvcTest(InvoiceCommandController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("InvoiceCommandController 테스트")
class InvoiceCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EasyPostInvoiceSaveService easyPostInvoiceSaveService;

    @MockitoBean
    private ManualOrderInvoiceService manualOrderInvoiceService;

    @Nested
    @DisplayName("단건 송장 발급 테스트")
    class CreateShipmentInvoiceTests {

        private static final String REQUEST_BODY = """
                {"shipment":{"to_address":{"name":"John","street1":"417 Montgomery St","city":"San Francisco","state":"CA","zip":"94104","country":"US"},"from_address":{"name":"EasyPost","street1":"417 Montgomery St","city":"San Francisco","state":"CA","zip":"94104","country":"US"},"parcel":{"weight":21.9,"length":10.0,"width":8.0,"height":4.0}}}
                """;

        @Test
        @DisplayName("유효한 송장 발급 요청이 주어지면 단건 송장 발급을 요청했을 때 HTTP 200 응답과 송장 정보를 반환해야 한다")
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
        @DisplayName("운임 정보가 없는 요청이 주어지면 단건 송장 발급을 요청했을 때 HTTP 404 응답을 반환해야 한다")
        void createShipmentInvoice_noRates_returns404() throws Exception {
            given(easyPostInvoiceSaveService.createAndSaveInvoice(any()))
                    .willThrow(new BusinessException(ErrorCode.NO_SHIPPING_RATES));

            mockMvc.perform(post("/integrations/seller/orders/invoice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-104"))
                    .andExpect(jsonPath("$.message").value("운임 정보가 없습니다."));
        }

        @Test
        @DisplayName("GET 메서드로 단건 송장 발급을 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void createShipmentInvoice_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/invoice"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("일괄 송장 발급 테스트")
    class CreateBulkShipmentInvoiceTests {

        private static final String REQUEST_BODY = """
                {"sellerId":"seller-001","fromAddress":{"name":"EasyPost","street1":"417 Montgomery St","city":"San Francisco","state":"CA","zip":"94104","country":"US"},"parcel":{"weight":21.9,"length":10.0,"width":8.0,"height":4.0}}
                """;

        @Test
        @DisplayName("유효한 일괄 송장 발급 요청이 주어지면 일괄 송장 발급을 요청했을 때 HTTP 200 응답과 처리 건수를 반환해야 한다")
        void createBulkShipmentInvoice_returnsOk() throws Exception {
            given(easyPostInvoiceSaveService.createAndSaveBulkInvoices(any(), any(), any()))
                    .willReturn(new BulkInvoiceResponse(2, 0));

            mockMvc.perform(post("/integrations/seller/orders/bulk-invoice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.successCount").value(2))
                    .andExpect(jsonPath("$.data.failCount").value(0));
        }

        @Test
        @DisplayName("내부 서버 오류가 발생하면 일괄 송장 발급을 요청했을 때 HTTP 500 응답을 반환해야 한다")
        void createBulkShipmentInvoice_unexpectedError_returns500() throws Exception {
            given(easyPostInvoiceSaveService.createAndSaveBulkInvoices(any(), any(), any()))
                    .willThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "DB 연결 오류"));

            mockMvc.perform(post("/integrations/seller/orders/bulk-invoice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-500"))
                    .andExpect(jsonPath("$.message").value("DB 연결 오류"));
        }

        @Test
        @DisplayName("GET 메서드로 일괄 송장 발급을 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void createBulkShipmentInvoice_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/bulk-invoice"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("수동 주문 송장 발급 테스트")
    class CreateManualOrderInvoiceTests {

        private static final String REQUEST_BODY = """
                {
                  "orderId": "ORD-MANUAL-001",
                  "receiverName": "홍길동",
                  "receiverPhoneNo": "010-1234-5678",
                  "shipToAddress1": "123 Main St",
                  "shipToState": "CA",
                  "shipToCity": "Los Angeles",
                  "shipToZipCode": "90001",
                  "items": [{"skuId": "SKU-001", "productNameSnapshot": "상품명", "quantity": 2}],
                  "fromAddress": {"name": "CONK Warehouse", "street1": "456 Warehouse Blvd",
                                  "city": "Los Angeles", "state": "CA", "zip": "90002", "country": "US"},
                  "parcel": {"weight": 10.0, "length": 10.0, "width": 8.0, "height": 4.0}
                }
                """;

        @Test
        @DisplayName("유효한 수동 주문 요청이 주어지면 수동 주문 송장 발급을 요청했을 때 HTTP 200 응답과 주문 및 송장 정보를 반환해야 한다")
        void createManualOrderInvoice_returnsOk() throws Exception {
            List<ManualOrderInvoiceResponse.OrderItemBody> items =
                    List.of(new ManualOrderInvoiceResponse.OrderItemBody("SKU-001", "상품명", 2));
            ManualOrderInvoiceResponse response = new ManualOrderInvoiceResponse(
                    "ORD-MANUAL-001", "홍길동", "123 Main St, Los Angeles, CA, 90001",
                    items, "shp_manual_001", "TRK-001", "USPS", 550,
                    "https://track.easypost.com/TRK-001", "https://label.url/shp_manual_001.pdf"
            );
            given(manualOrderInvoiceService.issue(anyString(), any())).willReturn(response);

            mockMvc.perform(post("/integrations/seller/orders/manual-invoice")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("ORD-MANUAL-001"))
                    .andExpect(jsonPath("$.data.invoiceNo").value("shp_manual_001"))
                    .andExpect(jsonPath("$.data.carrierType").value("USPS"))
                    .andExpect(jsonPath("$.data.freightChargeAmt").value(550))
                    .andExpect(jsonPath("$.data.items[0].skuId").value("SKU-001"));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 수동 주문 송장 발급을 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void createManualOrderInvoice_missingSellerIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/manual-invoice")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("필수 헤더가 누락되었습니다: X-Seller-Id"));
        }

        @Test
        @DisplayName("이미 송장이 발급된 주문 정보가 주어지면 수동 주문 송장 발급을 요청했을 때 HTTP 409 응답을 반환해야 한다")
        void createManualOrderInvoice_alreadyInvoiced_returns409() throws Exception {
            given(manualOrderInvoiceService.issue(anyString(), any()))
                    .willThrow(new BusinessException(ErrorCode.INVOICE_ALREADY_EXISTS, "이미 송장이 발급된 주문입니다: ORD-MANUAL-001"));

            mockMvc.perform(post("/integrations/seller/orders/manual-invoice")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-201"))
                    .andExpect(jsonPath("$.message").value("이미 송장이 발급된 주문입니다: ORD-MANUAL-001"));
        }

        @Test
        @DisplayName("운임 정보가 없는 요청이 주어지면 수동 주문 송장 발급을 요청했을 때 HTTP 404 응답을 반환해야 한다")
        void createManualOrderInvoice_easyPostFails_returns404() throws Exception {
            given(manualOrderInvoiceService.issue(anyString(), any()))
                    .willThrow(new BusinessException(ErrorCode.NO_SHIPPING_RATES));

            mockMvc.perform(post("/integrations/seller/orders/manual-invoice")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-104"))
                    .andExpect(jsonPath("$.message").value("운임 정보가 없습니다."));
        }

        @Test
        @DisplayName("GET 메서드로 수동 주문 송장 발급을 요청했을 때 HTTP 405 응답을 반환해야 한다")
        void createManualOrderInvoice_wrongMethod_returns405() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders/manual-invoice")
                            .header("X-Seller-Id", "seller-001"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("서비스에서 예기치 않은 예외가 발생하면 수동 주문 송장 발급을 요청했을 때 HTTP 500 응답을 반환해야 한다")
        void createManualOrderInvoice_unexpectedError_returns500() throws Exception {
            given(manualOrderInvoiceService.issue(anyString(), any()))
                    .willThrow(new RuntimeException("예상치 못한 서버 오류"));

            mockMvc.perform(post("/integrations/seller/orders/manual-invoice")
                            .header("X-Seller-Id", "seller-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
