package com.conk.integration.command.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

// bulkAssignInvoice 호출 시 (orderId, invoiceNo) 쌍을 전달하기 위한 DTO
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class OrderInvoicePair {
    private final String orderId;
    private final String invoiceNo;
}
