package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.request.BulkFulfillmentRequest;
import com.conk.integration.command.application.dto.request.BulkInvoiceRequest;
import com.conk.integration.command.application.dto.request.ChannelOrderSyncRequest;
import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.request.ManualOrderInvoiceRequest;
import com.conk.integration.command.application.dto.request.SellerChannelConnectRequest;
import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.BulkFulfillmentResponse;
import com.conk.integration.command.application.dto.response.BulkInvoiceResponse;
import com.conk.integration.command.application.dto.response.ChannelOrderImportResponse;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.application.dto.response.EasyPostInvoiceResponse;
import com.conk.integration.command.application.dto.response.ManualOrderInvoiceResponse;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
import com.conk.integration.command.application.service.ChannelOrderSyncDispatchService;
import com.conk.integration.command.application.service.EasyPostInvoiceSaveService;
import com.conk.integration.command.application.service.ManualOrderInvoiceService;
import com.conk.integration.command.application.service.SellerChannelConnectService;
import com.conk.integration.command.application.service.SellerChannelImportPreviewService;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.ApiResponse;
import com.conk.integration.common.channel.ChannelKeyResolver;
import com.conk.integration.common.channel.dto.SellerChannelDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 통합 command API 엔드포인트를 노출한다.
@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class IntegrationCommandController {

    private final ChannelFulfillmentDispatchService fulfillmentDispatchService;
    private final EasyPostInvoiceSaveService easyPostInvoiceSaveService;
    private final ChannelOrderSyncDispatchService orderSyncDispatchService;
    private final ManualOrderInvoiceService manualOrderInvoiceService;
    private final SellerChannelConnectService sellerChannelConnectService;
    private final SellerChannelImportPreviewService sellerChannelImportPreviewService;

    /**
     * 셀러 채널 연결
     * POST /integrations/seller/channels/{channelKey}/connect
     */
    @PostMapping("/seller/channels/{channelKey}/connect")
    public ResponseEntity<ApiResponse<SellerChannelDetailDto>> connectSellerChannel(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String channelKey,
            @RequestBody SellerChannelConnectRequest request) {

        SellerChannelDetailDto response = sellerChannelConnectService.connect(sellerId, channelKey, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 채널 주문 동기화
     * POST /integrations/seller/orders/sync
     */
    @PostMapping("/seller/orders/sync")
    public ResponseEntity<ApiResponse<ChannelOrderSyncResponse>> syncChannelOrders(
            @RequestHeader("X-Seller-Id") String sellerId,
            @RequestBody ChannelOrderSyncRequest request) {

        ChannelOrderSyncResponse response = orderSyncDispatchService.sync(
                sellerId, request.getOrderChannel());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 채널 주문 동기화
     * POST /integrations/seller/channels/{channelKey}/sync
     */
    @PostMapping("/seller/channels/{channelKey}/sync")
    public ResponseEntity<ApiResponse<ChannelOrderSyncResponse>> syncChannelOrdersByChannel(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String channelKey) {

        ChannelOrderSyncResponse response = syncChannelOrdersByChannelKey(sellerId, channelKey);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 채널 주문 가져오기
     * POST /integrations/seller/channels/{channelKey}/import-orders
     */
    @PostMapping("/seller/channels/{channelKey}/import-orders")
    public ResponseEntity<ApiResponse<ChannelOrderImportResponse>> importChannelOrders(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String channelKey) {

        ChannelOrderSyncResponse response = syncChannelOrdersByChannelKey(sellerId, channelKey);
        return ResponseEntity.ok(ApiResponse.ok(ChannelOrderImportResponse.from(response)));
    }

    /**
     * 채널 주문 가져오기 미리보기
     * POST /integrations/seller/channels/{channelKey}/import-preview
     */
    @PostMapping("/seller/channels/{channelKey}/import-preview")
    public ResponseEntity<ApiResponse<SellerChannelImportPreviewResponse>> importChannelPreview(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String channelKey,
            @RequestBody(required = false) SellerChannelImportPreviewRequest request) {

        SellerChannelImportPreviewResponse response = sellerChannelImportPreviewService.preview(
                sellerId, toOrderChannel(channelKey), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 셀러 주문 fulfillment 생성
     * POST /integrations/seller/orders/fulfillment/{orderId}
     */
    @PostMapping("/seller/orders/fulfillment/{orderId}")
    public ResponseEntity<ApiResponse<Void>> createSellerOrderFulfillment(
            @PathVariable String orderId) {

        fulfillmentDispatchService.fulfill(orderId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * 미전송 주문 일괄 fulfillment 전송
     * POST /integrations/seller/orders/bulk-fulfillment
     */
    @PostMapping("/seller/orders/bulk-fulfillment")
    public ResponseEntity<ApiResponse<BulkFulfillmentResponse>> createBulkFulfillment(
            @RequestHeader("X-Seller-Id") String sellerId,
            @RequestBody BulkFulfillmentRequest request) {

        BulkFulfillmentResponse response = fulfillmentDispatchService.fulfillBulk(
                sellerId, request.getOrderChannel());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * EasyPost 단건 송장 발급
     * POST /integrations/seller/orders/invoice
     */
    @PostMapping("/seller/orders/invoice")
    public ResponseEntity<ApiResponse<EasyPostInvoiceResponse>> createShipmentInvoice(
            @RequestBody EasyPostCreateShipmentRequest request) {

        EasypostShipmentInvoice invoice = easyPostInvoiceSaveService.createAndSaveInvoice(request);
        return ResponseEntity.ok(ApiResponse.ok(EasyPostInvoiceResponse.from(invoice)));
    }

    /**
     * EasyPost 일괄 송장 발급
     * POST /integrations/seller/orders/bulk-invoice
     */
    @PostMapping("/seller/orders/bulk-invoice")
    public ResponseEntity<ApiResponse<BulkInvoiceResponse>> createBulkShipmentInvoice(
            @RequestBody BulkInvoiceRequest request) {

        BulkInvoiceResponse response = easyPostInvoiceSaveService.createAndSaveBulkInvoices(
                request.getSellerId(), request.getFromAddress(), request.getParcel());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 수동 주문 기입 및 EasyPost 송장 발급
     * POST /integrations/seller/orders/manual-invoice
     */
    @PostMapping("/seller/orders/manual-invoice")
    public ResponseEntity<ApiResponse<ManualOrderInvoiceResponse>> createManualOrderInvoice(
            @RequestHeader("X-Seller-Id") String sellerId,
            @RequestBody ManualOrderInvoiceRequest request) {

        ManualOrderInvoiceResponse response = manualOrderInvoiceService.issue(sellerId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * path variable channelKey를 주문 채널 enum으로 변환한다.
     *
     * @param channelKey 채널 코드 path variable
     * @return 변환된 주문 채널 enum
     */
    private OrderChannel toOrderChannel(String channelKey) {
        return ChannelKeyResolver.toOrderChannel(channelKey, "지원하지 않는 주문 동기화 채널입니다: ");
    }

    /**
     * channelKey 기준 주문 동기화와 주문 가져오기 엔드포인트가 공유하는 변환/호출 흐름이다.
     *
     * @param sellerId 셀러 식별자
     * @param channelKey 채널 코드 path variable
     * @return 채널 주문 동기화 결과
     */
    private ChannelOrderSyncResponse syncChannelOrdersByChannelKey(String sellerId, String channelKey) {
        OrderChannel orderChannel = toOrderChannel(channelKey);
        return orderSyncDispatchService.sync(sellerId, orderChannel);
    }
}
