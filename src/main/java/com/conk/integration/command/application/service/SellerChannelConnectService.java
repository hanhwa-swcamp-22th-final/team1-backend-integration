package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelConnectRequest;
import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import com.conk.integration.command.infrastructure.repository.ChannelApiRepository;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.dto.SellerChannelDetailDto;
import com.conk.integration.query.service.ShopifyPingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 셀러 채널 연결 요청을 검증하고 channel_api에 저장한다.
@Service
@RequiredArgsConstructor
public class SellerChannelConnectService {

    private static final String SHOPIFY = "SHOPIFY";

    private final ChannelApiRepository channelApiRepository;
    private final ShopifyPingClient shopifyPingClient;

    public SellerChannelDetailDto connect(String sellerId, String channelKey, SellerChannelConnectRequest request) {
        validateSellerId(sellerId);
        String normalizedChannelKey = normalizeChannelKey(channelKey);
        validateRequest(request);
        ensureSupportedChannel(normalizedChannelKey);
        ensureShopifyConnection(request.getStoreName(), request.getChannelApi());

        ChannelApiId id = new ChannelApiId(sellerId, normalizedChannelKey);
        ChannelApi channelApi = channelApiRepository.findById(id)
                .map(existing -> {
                    existing.updateConnection(request.getChannelApi().trim(), request.getStoreName().trim());
                    return existing;
                })
                .orElseGet(() -> ChannelApi.builder()
                        .id(id)
                        .channelApi(request.getChannelApi().trim())
                        .storeName(request.getStoreName().trim())
                        .build());

        ChannelApi saved = channelApiRepository.saveAndFlush(channelApi);
        return new SellerChannelDetailDto(
                saved.getId().getChannelName(),
                saved.getStoreName(),
                saved.getChannelApi(),
                saved.getAudit() != null ? saved.getAudit().getCreatedAt() : null);
    }

    private void ensureSupportedChannel(String channelKey) {
        if (!SHOPIFY.equals(channelKey)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CHANNEL,
                    "지원하지 않는 채널 연결 채널입니다: " + channelKey);
        }
    }

    private void ensureShopifyConnection(String storeName, String channelApi) {
        if (!shopifyPingClient.ping(storeName.trim(), channelApi.trim())) {
            throw new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND,
                    "Shopify 채널 연결에 실패했습니다.");
        }
    }

    private void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID);
        }
    }

    private String normalizeChannelKey(String channelKey) {
        String normalized = channelKey == null ? "" : channelKey.trim().toUpperCase();
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CHANNEL, "지원하지 않는 채널 연결 채널입니다: " + channelKey);
        }
        return normalized;
    }

    private void validateRequest(SellerChannelConnectRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID, "채널 연결 요청 본문은 필수입니다.");
        }
        requireText(request.getStoreName(), "storeName");
        requireText(request.getChannelApi(), "channelApi");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID, fieldName + "는 필수입니다.");
        }
    }
}
