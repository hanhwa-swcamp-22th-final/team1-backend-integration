package com.conk.integration.command.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// EasyPost 일괄 송장 발급 결과를 성공/실패 건수로 요약한 응답 DTO다.
@Getter
@AllArgsConstructor
public class BulkInvoiceResponse {

    private int successCount;
    private int failCount;
}
