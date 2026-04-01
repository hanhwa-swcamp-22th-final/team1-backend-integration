package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.request.BulkFulfillmentRequest;
import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.BulkFulfillmentResponse;
import com.conk.integration.command.application.dto.response.EasyPostInvoiceResponse;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
import com.conk.integration.command.application.service.EasyPostInvoiceSaveService;
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

    /**
     * INT-003 — 셀러 주문 fulfillment 생성
     * POST /integrations/seller/orders/fulfillment/{orderId}
     */
    @PostMapping("/seller/orders/fulfillment/{orderId}")
    public ResponseEntity<ApiResponse<Void>> createSellerOrderFulfillment(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String orderId) {

        // 실제 bearer 파싱은 추후 security 계층에서 담당하고, 현재는 헤더 존재 계약만 강제한다.
        fulfillmentDispatchService.fulfill(orderId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * INT-004 — 미전송 주문 일괄 fulfillment 전송
     * POST /integrations/seller/orders/bulk-fulfillment
     */
    @PostMapping("/seller/orders/bulk-fulfillment")
    public ResponseEntity<ApiResponse<BulkFulfillmentResponse>> createBulkFulfillment(
            @RequestHeader("Authorization") String authorization,
            @RequestBody BulkFulfillmentRequest request) {

        // 실제 bearer 파싱은 추후 security 계층에서 담당하고, 현재는 헤더 존재 계약만 강제한다.
        BulkFulfillmentResponse response = fulfillmentDispatchService.fulfillBulk(
                request.getSellerId(), request.getOrderChannel());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * INT-005 — EasyPost 단건 송장 발급
     * POST /integrations/seller/orders/invoice
     */
    @PostMapping("/seller/orders/invoice")
    public ResponseEntity<ApiResponse<EasyPostInvoiceResponse>> createShipmentInvoice(
            @RequestHeader("Authorization") String authorization,
            @RequestBody EasyPostCreateShipmentRequest request) {

        // 실제 bearer 파싱은 추후 security 계층에서 담당하고, 현재는 헤더 존재 계약만 강제한다.
        EasypostShipmentInvoice invoice = easyPostInvoiceSaveService.createAndSaveInvoice(request);
        return ResponseEntity.ok(ApiResponse.ok(EasyPostInvoiceResponse.from(invoice)));
    }
}
