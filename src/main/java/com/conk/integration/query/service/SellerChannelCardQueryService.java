package com.conk.integration.query.service;

import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.query.mapper.SellerChannelCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// 채널 카드 raw 조회 결과에 표시용 라벨을 덧입혀 반환한다.
@Service
@RequiredArgsConstructor
public class SellerChannelCardQueryService {

    private final SellerChannelCardMapper channelCardMapper;

    /**
     * 셀러의 채널 연동 카드 목록을 조회하고 표시용 라벨을 부여해 반환한다.
     *
     * @param sellerId 셀러 식별자
     * @return 채널별 카드 목록 (label 필드 포함)
     * @throws IllegalArgumentException sellerId가 null이거나 공백인 경우
     */
    public List<SellerChannelCardDto> getChannelCards(String sellerId) {
        validateSellerId(sellerId);
        List<SellerChannelCardDto> cards = channelCardMapper.findBySellerIdGroupedByChannel(sellerId);
        // DB에는 코드값만 있으므로 화면 표시에 맞는 label을 후처리한다.
        cards.forEach(card -> card.setLabel(toLabel(card.getKey())));
        return cards;
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
            throw new IllegalArgumentException("sellerId는 필수입니다.");
        }
    }
}
