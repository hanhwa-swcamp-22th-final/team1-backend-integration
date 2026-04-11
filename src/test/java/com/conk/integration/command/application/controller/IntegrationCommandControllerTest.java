package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.controller.IntegrationCommandController;
import java.util.List;
import com.conk.integration.command.application.service.SellerChannelConnectService;
import com.conk.integration.command.application.dto.response.BulkFulfillmentResponse;
import com.conk.integration.command.application.dto.response.BulkInvoiceResponse;
import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.application.dto.response.ManualOrderInvoiceResponse;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
import com.conk.integration.command.application.service.ChannelOrderSyncDispatchService;
import com.conk.integration.command.application.service.EasyPostInvoiceSaveService;
import com.conk.integration.command.application.service.ManualOrderInvoiceService;
import com.conk.integration.command.application.service.SellerChannelImportPreviewService;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.common.exception.GlobalExceptionHandler;
import com.conk.integration.query.dto.SellerChannelDetailDto;
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

// fulfillment command API의 웹 계약을 슬라이스 테스트로 고정한다.
@WebMvcTest(IntegrationCommandController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("IntegrationCommandController 테스트")
class IntegrationCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChannelFulfillmentDispatchService fulfillmentDispatchService;

    @MockitoBean
    private EasyPostInvoiceSaveService easyPostInvoiceSaveService;

    @MockitoBean
    private ChannelOrderSyncDispatchService orderSyncDispatchService;

    @MockitoBean
    private ManualOrderInvoiceService manualOrderInvoiceService;

    @MockitoBean
    private SellerChannelConnectService sellerChannelConnectService;

    @MockitoBean
    private SellerChannelImportPreviewService sellerChannelImportPreviewService;

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
