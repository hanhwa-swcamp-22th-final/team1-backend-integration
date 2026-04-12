package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelConnectRequest;
import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import com.conk.integration.command.infrastructure.repository.ChannelApiRepository;
import com.conk.integration.common.SellerIdValidator;
import com.conk.integration.common.channel.ChannelKeyResolver;
import com.conk.integration.common.channel.ShopifyConnectionVerifier;
import com.conk.integration.common.channel.dto.SellerChannelDetailDto;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 셀러 채널 연결 요청을 검증하고 channel_api에 저장한다.
@Service
@RequiredArgsConstructor
public class SellerChannelConnectService {

    private static final String SHOPIFY = "SHOPIFY";

    private final ChannelApiRepository channelApiRepository;
    private final ShopifyConnectionVerifier shopifyConnectionVerifier;

    public SellerChannelDetailDto connect(String sellerId, String channelKey, SellerChannelConnectRequest request) {
        SellerIdValidator.requireValid(sellerId);
        String normalizedChannelKey = ChannelKeyResolver.normalize(
                channelKey,
                ErrorCode.UNSUPPORTED_CHANNEL,
                "지원하지 않는 채널 연결 채널입니다: " + channelKey);
        validateRequest(request);
        ChannelKeyResolver.requireSupported(
                normalizedChannelKey,
                SHOPIFY,
                "지원하지 않는 채널 연결 채널입니다: ");
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

    /**
     * 저장 전 Shopify 경량 ping을 호출해 실제 연결 가능 여부를 확인한다.
     *
     * @param storeName Shopify 스토어명
     * @param channelApi Shopify Admin API 액세스 토큰
     * @throws BusinessException Shopify 연결 검증에 실패한 경우 (INT-404)
     */
    private void ensureShopifyConnection(String storeName, String channelApi) {
        if (!shopifyConnectionVerifier.ping(storeName.trim(), channelApi.trim())) {
            throw new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND,
                    "Shopify 채널 연결에 실패했습니다.");
        }
    }

    /**
     * 채널 연결 요청 본문과 필수 텍스트 필드를 검증한다.
     *
     * @param request 채널 연결 요청 본문
     * @throws BusinessException 요청 본문이나 필수 필드가 비어 있는 경우 (INT-001)
     */
    private void validateRequest(SellerChannelConnectRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID, "채널 연결 요청 본문은 필수입니다.");
        }
        requireText(request.getStoreName(), "storeName");
        requireText(request.getChannelApi(), "channelApi");
    }

    /**
     * 문자열 필드가 null이거나 공백인지 확인한다.
     *
     * @param value 검증할 값
     * @param fieldName 예외 메시지에 표시할 필드명
     * @throws BusinessException 값이 비어 있는 경우 (INT-001)
     */
    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID, fieldName + "는 필수입니다.");
        }
    }
}
