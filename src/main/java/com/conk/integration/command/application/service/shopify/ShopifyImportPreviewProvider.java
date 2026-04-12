package com.conk.integration.command.application.service.shopify;

import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.application.service.ChannelImportPreviewProvider;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyOrderClient;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.query.service.ChannelApiQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Shopify 채널 import preview 구현체다.
 */
@Service
@RequiredArgsConstructor
public class ShopifyImportPreviewProvider implements ChannelImportPreviewProvider {

    private final ChannelApiQueryService channelApiQueryService;
    private final ChannelOrderRepository channelOrderRepository;
    private final ShopifyOrderClient shopifyOrderClient;

    @Override
    public boolean supports(OrderChannel channel) {
        return channel == OrderChannel.SHOPIFY;
    }

    @Override
    public SellerChannelImportPreviewResponse preview(
            String sellerId,
            SellerChannelImportPreviewRequest request) {

        ChannelCredential credential = channelApiQueryService.findChannelCredential(sellerId, OrderChannel.SHOPIFY.name());
        LocalDateTime lastSyncedAt = findLatestCreatedAt(sellerId, OrderChannel.SHOPIFY);
        LocalDateTime since = resolveSince(lastSyncedAt, request);

        int pendingOrders = shopifyOrderClient.countOrdersSince(
                credential.getStoreName(),
                credential.getChannelApi(),
                since);

        return new SellerChannelImportPreviewResponse(pendingOrders, lastSyncedAt);
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
