package com.conk.integration.query.service;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.dto.SellerChannelDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 채널 상세 연결 정보를 조회하고 필요 시 실연결 여부를 검증한다.
@Service
@RequiredArgsConstructor
public class SellerChannelDetailQueryService {

    private final ChannelApiQueryService channelApiQueryService;
    private final ShopifyPingClient shopifyPingClient;

    /**
     * 셀러의 특정 채널 연결 상세를 조회한다.
     * SHOPIFY 채널은 실제 API ping 성공 시에만 연결된 상태로 본다.
     *
     * @param sellerId 셀러 식별자
     * @param channelKey 채널 코드
     * @return 연결된 채널 상세 정보
     */
    public SellerChannelDetailDto getChannelDetail(String sellerId, String channelKey) {
        validateSellerId(sellerId);
        String normalizedChannelKey = normalizeChannelKey(channelKey);

        ChannelApi channelApi = channelApiQueryService.findChannelApi(sellerId, normalizedChannelKey);
        ensureConnected(channelApi);

        return new SellerChannelDetailDto(
                channelApi.getId().getChannelName(),
                channelApi.getStoreName(),
                channelApi.getChannelApi(),
                resolveConnectedAt(channelApi.getAudit()));
    }

    private void ensureConnected(ChannelApi channelApi) {
        if (!"SHOPIFY".equalsIgnoreCase(channelApi.getId().getChannelName())) {
            return;
        }

        boolean connected = shopifyPingClient.ping(channelApi.getStoreName(), channelApi.getChannelApi());
        if (!connected) {
            throw new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
        }
    }

    private String normalizeChannelKey(String channelKey) {
        String normalized = channelKey == null ? "" : channelKey.trim().toUpperCase();
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
        }
        return normalized;
    }

    private LocalDateTime resolveConnectedAt(AuditFields auditFields) {
        return auditFields != null ? auditFields.getCreatedAt() : null;
    }

    private void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID);
        }
    }
}
