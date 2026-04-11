package com.conk.integration.command.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

// 셀러 채널 주문 가져오기 미리보기 결과를 반환하는 응답 DTO다.
@Getter
@AllArgsConstructor
public class SellerChannelImportPreviewResponse {

    private int pendingOrders;
    private LocalDateTime lastSyncedAt;
}
