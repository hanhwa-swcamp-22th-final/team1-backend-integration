package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.ShopifyOrderClient;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.dto.ShopifyCredentialDto;
import com.conk.integration.query.service.ChannelApiQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

// 셀러 채널 주문 가져오기 미리보기를 수행하는 read-only command 서비스다.
@Service
@RequiredArgsConstructor
public class SellerChannelImportPreviewService {

    private final ChannelApiQueryService channelApiQueryService;
    private final ChannelOrderRepository channelOrderRepository;
    private final ShopifyOrderClient shopifyOrderClient;

    public SellerChannelImportPreviewResponse preview(
            String sellerId,
            OrderChannel orderChannel,
            SellerChannelImportPreviewRequest request) {

        validateSellerId(sellerId);
        ensureSupportedChannel(orderChannel);

        ShopifyCredentialDto credential = channelApiQueryService.findShopifyCredential(sellerId);
        LocalDateTime lastSyncedAt = findLatestCreatedAt(sellerId, orderChannel);
        LocalDateTime since = resolveSince(lastSyncedAt, request);

        int pendingOrders = shopifyOrderClient.countOrdersSince(
                credential.getStoreName(),
                credential.getAccessToken(),
                since);

        return new SellerChannelImportPreviewResponse(pendingOrders, lastSyncedAt);
    }

    private void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID);
        }
    }

    private void ensureSupportedChannel(OrderChannel orderChannel) {
        if (orderChannel != OrderChannel.SHOPIFY) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_CHANNEL,
                    "지원하지 않는 주문 동기화 채널입니다: " + orderChannel);
        }
    }

    private LocalDateTime findLatestCreatedAt(String sellerId, OrderChannel orderChannel) {
        return channelOrderRepository.findFirstBySellerIdAndOrderChannelOrderByAuditCreatedAtDesc(
                        sellerId,
                        orderChannel)
                .map(order -> order.getAudit())
                .filter(audit -> audit != null && audit.getCreatedAt() != null)
                .map(audit -> audit.getCreatedAt())
                .orElse(null);
    }

    private LocalDateTime resolveSince(LocalDateTime lastSyncedAt, SellerChannelImportPreviewRequest request) {
        if (lastSyncedAt != null) {
            return lastSyncedAt;
        }

        return LocalDateTime.now().minus(parseSyncWindow(request));
    }

    private Duration parseSyncWindow(SellerChannelImportPreviewRequest request) {
        String syncWindow = request != null ? request.getSyncWindow() : null;
        String normalized = syncWindow == null ? "" : syncWindow.trim();

        return switch (normalized) {
            case "최근 1일" -> Duration.ofDays(1);
            case "최근 3일" -> Duration.ofDays(3);
            case "최근 14일" -> Duration.ofDays(14);
            case "최근 7일" -> Duration.ofDays(7);
            default -> Duration.ofDays(7);
        };
    }
}
