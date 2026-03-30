package com.conk.integration.command.presentation;

import com.conk.integration.command.application.dto.response.ApiResponse;
import com.conk.integration.command.application.dto.response.SellerChannelCardDto;
import com.conk.integration.command.application.dto.response.SellerChannelOrderDto;
import com.conk.integration.command.application.service.SellerChannelCardQueryService;
import com.conk.integration.command.application.service.SellerChannelOrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final SellerChannelCardQueryService channelCardQueryService;
    private final SellerChannelOrderQueryService channelOrderQueryService;

    /**
     * INT-001 — 셀러 채널 연결 카드 조회
     * GET /integrations/seller/channels
     */
    @GetMapping("/seller/channels")
    public ResponseEntity<ApiResponse<List<SellerChannelCardDto>>> getSellerChannelCards(
            @RequestHeader("X-Seller-Id") String sellerId) {

        List<SellerChannelCardDto> data = channelCardQueryService.getChannelCards(sellerId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /**
     * INT-002 — 셀러 채널 통합 주문 조회
     * GET /integrations/seller/orders
     */
    @GetMapping("/seller/orders")
    public ResponseEntity<ApiResponse<List<SellerChannelOrderDto>>> getSellerChannelOrders(
            @RequestHeader("X-Seller-Id") String sellerId) {

        List<SellerChannelOrderDto> data = channelOrderQueryService.getOrders(sellerId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
