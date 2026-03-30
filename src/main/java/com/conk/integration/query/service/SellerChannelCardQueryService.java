package com.conk.integration.query.service;

import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.query.mapper.SellerChannelCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerChannelCardQueryService {

    private final SellerChannelCardMapper channelCardMapper;

    public List<SellerChannelCardDto> getChannelCards(String sellerId) {
        validateSellerId(sellerId);
        List<SellerChannelCardDto> cards = channelCardMapper.findBySellerIdGroupedByChannel(sellerId);
        cards.forEach(card -> card.setLabel(toLabel(card.getKey())));
        return cards;
    }

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

    private void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId는 필수입니다.");
        }
    }
}
