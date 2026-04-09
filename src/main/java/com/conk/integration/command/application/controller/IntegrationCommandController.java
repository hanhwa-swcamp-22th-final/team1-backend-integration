package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.request.BulkFulfillmentRequest;
import com.conk.integration.command.application.dto.request.BulkInvoiceRequest;
import com.conk.integration.command.application.dto.request.ChannelOrderSyncRequest;
import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.request.ManualOrderInvoiceRequest;
import com.conk.integration.command.application.dto.response.BulkFulfillmentResponse;
import com.conk.integration.command.application.dto.response.BulkInvoiceResponse;
import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.application.dto.response.EasyPostInvoiceResponse;
import com.conk.integration.command.application.dto.response.ManualOrderInvoiceResponse;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
import com.conk.integration.command.application.service.ChannelOrderSyncDispatchService;
import com.conk.integration.command.application.service.EasyPostInvoiceSaveService;
import com.conk.integration.command.application.service.ManualOrderInvoiceService;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.common.ApiResponse;
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

    /**
     * INT-007 — 채널 주문 동기화
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
     * INT-003 — 셀러 주문 fulfillment 생성
     * POST /integrations/seller/orders/fulfillment/{orderId}
     */
    @PostMapping("/seller/orders/fulfillment/{orderId}")
    public ResponseEntity<ApiResponse<Void>> createSellerOrderFulfillment(
            @PathVariable String orderId) {

        fulfillmentDispatchService.fulfill(orderId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * INT-004 — 미전송 주문 일괄 fulfillment 전송
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
     * INT-005 — EasyPost 단건 송장 발급
     * POST /integrations/seller/orders/invoice
     */
    @PostMapping("/seller/orders/invoice")
    public ResponseEntity<ApiResponse<EasyPostInvoiceResponse>> createShipmentInvoice(
            @RequestBody EasyPostCreateShipmentRequest request) {

        EasypostShipmentInvoice invoice = easyPostInvoiceSaveService.createAndSaveInvoice(request);
        return ResponseEntity.ok(ApiResponse.ok(EasyPostInvoiceResponse.from(invoice)));
    }

    /**
     * INT-006 — EasyPost 일괄 송장 발급
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
     * INT-008 — 수동 주문 기입 및 EasyPost 송장 발급
     * POST /integrations/seller/orders/manual-invoice
     */
    @PostMapping("/seller/orders/manual-invoice")
    public ResponseEntity<ApiResponse<ManualOrderInvoiceResponse>> createManualOrderInvoice(
            @RequestHeader("X-Seller-Id") String sellerId,
            @RequestBody ManualOrderInvoiceRequest request) {

        ManualOrderInvoiceResponse response = manualOrderInvoiceService.issue(sellerId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
