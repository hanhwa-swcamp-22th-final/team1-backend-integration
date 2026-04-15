package com.conk.integration.query.service;

import com.conk.integration.common.channel.ChannelConnectionVerifier;
import com.conk.integration.common.channel.ChannelCredentialReader;
import com.conk.integration.common.channel.ChannelLabelResolver;
import com.conk.integration.common.SellerIdValidator;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.query.mapper.SellerChannelCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 채널 카드 raw 조회 결과에 표시용 라벨과 실시간 연동 상태를 덧입혀 반환한다.
@Service
@RequiredArgsConstructor
public class SellerChannelCardQueryService {

    private static final String SHOPIFY_CHANNEL_KEY = "SHOPIFY";
    private static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    private final SellerChannelCardMapper channelCardMapper;
    private final List<ChannelCredentialReader> credentialReaders;
    private final List<ChannelConnectionVerifier> connectionVerifiers;

    /**
     * 셀러의 채널 연동 카드 목록을 조회하고 표시용 라벨을 부여해 반환한다.
     * SHOPIFY 채널은 실제 API ping 결과로 syncStatus를 덮어쓴다.
     *
     * @param sellerId 셀러 식별자
     * @return 채널별 카드 목록 (label, syncStatus 포함)
     * @throws BusinessException sellerId가 null이거나 공백인 경우 (INT-001)
     */
    public List<SellerChannelCardDto> getChannelCards(String sellerId) {
        SellerIdValidator.requireValid(sellerId);
        List<SellerChannelCardDto> cards = new ArrayList<>(channelCardMapper.findBySellerIdGroupedByChannel(sellerId));
        ensureDefaultShopifyCard(cards);
        cards.forEach(card -> {
            // DB에는 코드값만 있으므로 화면 표시에 맞는 label을 후처리한다.
            card.setLabel(ChannelLabelResolver.toLabel(card.getKey()));
            Optional<String> syncStatus = resolveChannelSyncStatus(sellerId, card.getKey());
            if (syncStatus.isPresent()) {
                card.setSyncStatus(syncStatus.get());
            }
        });
        return cards;
    }

    private void ensureDefaultShopifyCard(List<SellerChannelCardDto> cards) {
        boolean hasShopifyCard = cards.stream()
                .map(SellerChannelCardDto::getKey)
                .anyMatch(SHOPIFY_CHANNEL_KEY::equalsIgnoreCase);

        if (hasShopifyCard) {
            return;
        }

        SellerChannelCardDto defaultCard = new SellerChannelCardDto();
        defaultCard.setKey(SHOPIFY_CHANNEL_KEY);
        defaultCard.setLabel(ChannelLabelResolver.toLabel(SHOPIFY_CHANNEL_KEY));
        defaultCard.setSyncStatus(NOT_CONFIGURED);
        defaultCard.setPendingOrders(0);
        defaultCard.setTodayImported(0);
        defaultCard.setLastSyncedAt(null);
        cards.add(defaultCard);
    }

    /**
     * Shopify 자격증명 조회 후 ping 결과를 syncStatus 문자열로 변환한다.
     *
     * @param sellerId 셀러 식별자
     * @return CONNECTED, DISCONNECTED, NOT_CONFIGURED 중 하나
     */
    private Optional<String> resolveChannelSyncStatus(String sellerId, String channelKey) {
        Optional<ChannelCredentialReader> reader = findCredentialReader(channelKey);
        Optional<ChannelConnectionVerifier> verifier = findConnectionVerifier(channelKey);

        if (reader.isEmpty() || verifier.isEmpty()) {
            return Optional.empty();
        }

        try {
            ChannelCredential credential = reader.get().read(sellerId, channelKey);
            return Optional.of(
                    verifier.get().verify(credential.getStoreName(), credential.getChannelApi())
                            ? "CONNECTED"
                            : "DISCONNECTED");
        } catch (BusinessException e) {
            return Optional.of(NOT_CONFIGURED);
        }
    }

    private Optional<ChannelCredentialReader> findCredentialReader(String channelKey) {
        return credentialReaders.stream()
                .filter(candidate -> candidate.supports(channelKey))
                .findFirst();
    }

    private Optional<ChannelConnectionVerifier> findConnectionVerifier(String channelKey) {
        return connectionVerifiers.stream()
                .filter(candidate -> candidate.supports(channelKey))
                .findFirst();
    }

}
