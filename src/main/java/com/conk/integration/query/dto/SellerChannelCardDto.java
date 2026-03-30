package com.conk.integration.query.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * INT-001 응답 DTO — 셀러 채널 연결 카드
 * MyBatis setter 주입 + service에서 label 후처리 필요 → @Getter @Setter
 */
@Getter
@Setter
@NoArgsConstructor
public class SellerChannelCardDto {

    // key는 저장/조회용 채널 코드, label은 화면 표시용 문자열이다.
    private String key;
    private String label;
    private String syncStatus;
    private int pendingOrders;
    private int todayImported;
    private LocalDateTime lastSyncedAt;
}
