package com.conk.integration.command.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 일괄 fulfillment 전송 결과를 표현한다.
@Getter
@AllArgsConstructor
public class BulkFulfillmentResponse {

    private int successCount;
    private int failCount;
}
