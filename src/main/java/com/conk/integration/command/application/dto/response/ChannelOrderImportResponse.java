package com.conk.integration.command.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// 채널 주문 가져오기 결과를 건수 요약과 저장된 주문 목록으로 반환한다.
@Getter
@AllArgsConstructor
public class ChannelOrderImportResponse {

    private int importedCount;
    private int skippedCount;
    private int failedSyncCount;
    private List<ChannelOrderSyncResponse.OrderDto> orders;

    public static ChannelOrderImportResponse from(ChannelOrderSyncResponse response) {
        return new ChannelOrderImportResponse(
                response.getSavedCount(),
                response.getSkippedCount(),
                response.getFailedSyncCount(),
                response.getOrders());
    }
}
