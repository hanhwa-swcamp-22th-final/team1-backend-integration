package com.conk.integration.query.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SellerChannelOrderMapper 원시 결과 객체
 * MyBatis → service 변환 전 중간 DTO
 */
@Data
public class SellerChannelOrderQueryResult {

    private String orderId;
    private String channelOrderNo;
    private String orderChannel;
    private LocalDateTime orderedAt;
    private String receiverName;
    private String invoiceNo;
    private String shippedAt;
    private String firstItemName;
    private int itemCount;
}
