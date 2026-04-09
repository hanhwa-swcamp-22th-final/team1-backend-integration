package com.conk.integration.query.service;

import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.query.dto.ShopifyCredentialDto;
import com.conk.integration.query.mapper.SellerChannelCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// 채널 카드 raw 조회 결과에 표시용 라벨과 실시간 연동 상태를 덧입혀 반환한다.
@Service
@RequiredArgsConstructor
public class SellerChannelCardQueryService {

    private final SellerChannelCardMapper channelCardMapper;
    private final ChannelApiQueryService channelApiQueryService;
    private final ShopifyPingClient shopifyPingClient;

    /**
     * 셀러의 채널 연동 카드 목록을 조회하고 표시용 라벨을 부여해 반환한다.
     * SHOPIFY 채널은 실제 API ping 결과로 syncStatus를 덮어쓴다.
     *
     * @param sellerId 셀러 식별자
     * @return 채널별 카드 목록 (label, syncStatus 포함)
     * @throws BusinessException sellerId가 null이거나 공백인 경우 (INT-001)
     */
    public List<SellerChannelCardDto> getChannelCards(String sellerId) {
        validateSellerId(sellerId);
        List<SellerChannelCardDto> cards = channelCardMapper.findBySellerIdGroupedByChannel(sellerId);
        cards.forEach(card -> {
            // DB에는 코드값만 있으므로 화면 표시에 맞는 label을 후처리한다.
            card.setLabel(toLabel(card.getKey()));
            // SHOPIFY 채널은 주문 존재 여부 대신 실제 API 연동 상태를 반영한다.
            if ("SHOPIFY".equals(card.getKey())) {
                card.setSyncStatus(resolveShopifySyncStatus(sellerId));
            }
        });
        return cards;
    }

    // Shopify 자격증명 조회 후 ping 결과를 syncStatus 문자열로 변환한다.
    private String resolveShopifySyncStatus(String sellerId) {
        try {
            ShopifyCredentialDto cred = channelApiQueryService.findShopifyCredential(sellerId);
            return shopifyPingClient.ping(cred.getStoreName(), cred.getAccessToken())
                    ? "CONNECTED" : "DISCONNECTED";
        } catch (BusinessException e) {
            // 자격증명이 DB에 없는 경우 (CHANNEL_CREDENTIALS_NOT_FOUND, INT-103)
            return "NOT_CONFIGURED";
        }
    }

    // 알려진 채널 코드를 사람이 읽기 쉬운 라벨로 바꾼다.
    String toLabel(String channelName) {
        if (channelName == null) return "";
        return switch (channelName.toUpperCase()) {
            case "SHOPIFY" -> "Shopify";
            case "AMAZON"  -> "Amazon";
            case "MANUAL"  -> "Manual";
            case "EXCEL"   -> "Excel";
            default        -> channelName;
        };
    }

    // 모든 셀러 기준 조회 API는 X-Seller-Id 입력 검증을 공통으로 수행한다.
    private void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID);
        }
    }
}
