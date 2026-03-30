package com.conk.integration.command.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * INT-001 응답 DTO — 셀러 채널 연결 카드
 * description, actions[] 필드는 제거됨
 */
@Getter
@Builder
public class SellerChannelCardDto {

    /** 채널 식별자 (예: SHOPIFY, AMAZON) */
    private String key;

    /** 채널 표시명 (예: Shopify, Amazon) */
    private String label;

    /** 채널 연결 상태 표시값 (ACTIVE / PLANNED) */
    private String syncStatus;

    /** 미처리 대기 주문 수 (invoiceNo 미발급 기준) */
    private int pendingOrders;

    /** 오늘 수집된 주문 수 (created_at 기준) */
    private int todayImported;

    /** 마지막 동기화 시각 (Channel_Order 최근 생성 시각 기준) */
    private LocalDateTime lastSyncedAt;
}
