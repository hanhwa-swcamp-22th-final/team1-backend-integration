package com.conk.integration.query.dto;

import lombok.Data;

// 일괄 송장 발급 대상 조회에 필요한 주문 주소 필드만 담는 MyBatis 조회 결과 DTO다.
@Data
public class InvoiceTargetDto {

    private String orderId;
    private String receiverName;
    private String receiverPhoneNo;
    private String shipToAddress1;
    private String shipToCity;
    private String shipToState;
    private String shipToZipCode;
}