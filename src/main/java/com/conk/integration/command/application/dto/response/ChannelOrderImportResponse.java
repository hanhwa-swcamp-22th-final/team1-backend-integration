package com.conk.integration.command.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 채널 주문 가져오기 결과를 imported/skipped 건수로 요약한다.
@Getter
@AllArgsConstructor
public class ChannelOrderImportResponse {

    private int importedCount;
    private int skippedCount;

    public static ChannelOrderImportResponse from(ChannelOrderSyncResponse response) {
        return new ChannelOrderImportResponse(response.getSavedCount(), response.getSkippedCount());
    }
}
