package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.request.SellerChannelConnectRequest;
import com.conk.integration.command.application.service.SellerChannelConnectService;
import com.conk.integration.common.ApiResponse;
import com.conk.integration.common.channel.dto.SellerChannelDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 셀러 채널 연결 command API 엔드포인트를 노출한다.
@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class SellerChannelCommandController {

    private final SellerChannelConnectService sellerChannelConnectService;

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
}
