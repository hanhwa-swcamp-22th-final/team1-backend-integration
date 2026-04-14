package com.conk.integration.query.controller;

import com.conk.integration.common.ApiResponse;
import com.conk.integration.common.channel.dto.SellerChannelDetailDto;
import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.query.dto.SellerChannelOrderDto;
import com.conk.integration.query.dto.SellerOrderQueryParams;
import com.conk.integration.query.service.SellerChannelCardQueryService;
import com.conk.integration.query.service.SellerChannelDetailQueryService;
import com.conk.integration.query.service.SellerChannelOrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 셀러 기준 통합 조회 API 엔드포인트를 노출한다.
@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class IntegrationQueryController {

    private final SellerChannelCardQueryService channelCardQueryService;
    private final SellerChannelDetailQueryService channelDetailQueryService;
    private final SellerChannelOrderQueryService channelOrderQueryService;

    /**
     * 셀러 채널 연결 카드 조회
     * GET /integrations/seller/channels
     */
    @GetMapping("/seller/channels")
    public ResponseEntity<ApiResponse<List<SellerChannelCardDto>>> getSellerChannelCards(
            @RequestHeader("X-Seller-Id") String sellerId) {

        // 실제 조회와 후처리는 서비스에 위임하고 컨트롤러는 헤더/응답 래핑만 담당한다.
        return ResponseEntity.ok(ApiResponse.ok(channelCardQueryService.getChannelCards(sellerId)));
    }

    /**
     * 셀러 채널 연결 상세 조회
     * GET /integrations/seller/channels/{channelKey}
     */
    @GetMapping("/seller/channels/{channelKey}")
    public ResponseEntity<ApiResponse<SellerChannelDetailDto>> getSellerChannelDetail(
            @RequestHeader("X-Seller-Id") String sellerId,
            @PathVariable String channelKey) {

        return ResponseEntity.ok(ApiResponse.ok(channelDetailQueryService.getChannelDetail(sellerId, channelKey)));
    }

    /**
     * 셀러 채널 통합 주문 조회
     * GET /integrations/seller/orders?channel=SHOPIFY&search=...&page=0&size=20
     */
    @GetMapping("/seller/orders")
    public ResponseEntity<ApiResponse<List<SellerChannelOrderDto>>> getSellerChannelOrders(
            @RequestHeader("X-Seller-Id") String sellerId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        SellerOrderQueryParams params = SellerOrderQueryParams.builder()
                .sellerId(sellerId)
                .channel(channel)
                .search(search)
                .page(page)
                .size(size)
                .build();
        return ResponseEntity.ok(ApiResponse.ok(channelOrderQueryService.getOrders(params)));
    }
}
