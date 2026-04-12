package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.request.BulkFulfillmentRequest;
import com.conk.integration.command.application.dto.response.BulkFulfillmentResponse;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
import com.conk.integration.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 단건·일괄 fulfillment command API 엔드포인트를 노출한다.
@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class FulfillmentCommandController {

    private final ChannelFulfillmentDispatchService fulfillmentDispatchService;

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
}
