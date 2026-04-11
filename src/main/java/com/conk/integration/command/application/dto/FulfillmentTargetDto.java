package com.conk.integration.command.application.dto;

import lombok.Data;

/**
 * 미전송 주문 일괄 fulfillment 전송에 필요한 필드만 담는 DTO다.
 */
@Data
public class FulfillmentTargetDto {

    private String orderId;
    private String fulfillmentOrderId;
    private String invoiceNo;
    private String carrierType;
}
