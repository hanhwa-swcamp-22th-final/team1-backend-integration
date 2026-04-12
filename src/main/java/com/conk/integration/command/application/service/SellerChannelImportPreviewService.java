package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.ShopifyOrderClient;
import com.conk.integration.common.SellerIdValidator;
import com.conk.integration.common.channel.ChannelKeyResolver;
import com.conk.integration.common.channel.dto.ShopifyCredentialDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

// 셀러 채널 주문 가져오기 미리보기를 수행하는 read-only command 서비스다.
@Service
@RequiredArgsConstructor
public class SellerChannelImportPreviewService {

    private final ShopifyCredentialReader shopifyCredentialReader;
    private final ChannelOrderRepository channelOrderRepository;
    private final ShopifyOrderClient shopifyOrderClient;

    public SellerChannelImportPreviewResponse preview(
            String sellerId,
            OrderChannel orderChannel,
            SellerChannelImportPreviewRequest request) {

        SellerIdValidator.requireValid(sellerId);
        ChannelKeyResolver.requireSupported(
                orderChannel,
                OrderChannel.SHOPIFY,
                "지원하지 않는 주문 동기화 채널입니다: ");

        ShopifyCredentialDto credential = shopifyCredentialReader.findShopifyCredential(sellerId);
        LocalDateTime lastSyncedAt = findLatestCreatedAt(sellerId, orderChannel);
        LocalDateTime since = resolveSince(lastSyncedAt, request);

        int pendingOrders = shopifyOrderClient.countOrdersSince(
                credential.getStoreName(),
                credential.getAccessToken(),
                since);

        return new SellerChannelImportPreviewResponse(pendingOrders, lastSyncedAt);
    }

    /**
     * 동일 셀러/채널 기준으로 가장 최근 저장된 주문의 생성 시각을 조회한다.
     *
     * @param sellerId 셀러 식별자
     * @param orderChannel 대상 채널
     * @return 최근 저장 시각, 저장 이력이 없으면 null
     */
    private LocalDateTime findLatestCreatedAt(String sellerId, OrderChannel orderChannel) {
        return channelOrderRepository.findFirstBySellerIdAndOrderChannelOrderByAuditCreatedAtDesc(
                        sellerId,
                        orderChannel)
                .map(order -> order.getAudit())
                .filter(audit -> audit != null && audit.getCreatedAt() != null)
                .map(audit -> audit.getCreatedAt())
                .orElse(null);
    }

    /**
     * 미리보기 조회 시작 시각을 결정한다.
     * 최근 저장 이력이 있으면 그 시각을 사용하고, 없으면 syncWindow를 기준으로 계산한다.
     *
     * @param lastSyncedAt 최근 저장 시각
     * @param request 미리보기 요청 본문
     * @return Shopify 조회 시작 시각
     */
    private LocalDateTime resolveSince(LocalDateTime lastSyncedAt, SellerChannelImportPreviewRequest request) {
        if (lastSyncedAt != null) {
            return lastSyncedAt;
        }

        return LocalDateTime.now().minus(parseSyncWindow(request));
    }

    /**
     * 프론트에서 전달한 syncWindow 문자열을 Duration으로 변환한다.
     *
     * @param request 미리보기 요청 본문
     * @return 조회 기간 Duration
     */
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
