package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.request.ChannelOrderSyncRequest;
import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.ChannelOrderImportResponse;
import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.application.service.ChannelOrderSyncDispatchService;
import com.conk.integration.command.application.service.SellerChannelImportPreviewService;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.ApiResponse;
import com.conk.integration.common.channel.ChannelKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 채널 주문 동기화·가져오기·미리보기 command API 엔드포인트를 노출한다.
@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class OrderSyncCommandController {

    private final ChannelOrderSyncDispatchService orderSyncDispatchService;
    private final SellerChannelImportPreviewService sellerChannelImportPreviewService;

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
     * 채널 기준 주문 동기화
     * POST /integrations/seller/channels/{channelKey}/sync
     */
    @PostMapping("/seller/channels/{channelKey}/sync")
    public ResponseEntity<ApiResponse<ChannelOrderSyncResponse>> syncChannelOrdersByChannel(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String channelKey) {

        ChannelOrderSyncResponse response = syncByChannelKey(sellerId, channelKey);
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

        ChannelOrderSyncResponse response = syncByChannelKey(sellerId, channelKey);
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

    // channelKey 기준 주문 동기화와 주문 가져오기 엔드포인트가 공유하는 변환/호출 흐름이다.
    private ChannelOrderSyncResponse syncByChannelKey(String sellerId, String channelKey) {
        return orderSyncDispatchService.sync(sellerId, toOrderChannel(channelKey));
    }

    // path variable channelKey를 주문 채널 enum으로 변환한다.
    private OrderChannel toOrderChannel(String channelKey) {
        return ChannelKeyResolver.toOrderChannel(channelKey, "지원하지 않는 주문 동기화 채널입니다: ");
    }
}
