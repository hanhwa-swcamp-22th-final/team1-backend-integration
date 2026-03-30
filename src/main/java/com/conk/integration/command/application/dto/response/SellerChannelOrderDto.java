package com.conk.integration.command.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * INT-002 응답 DTO — 셀러 채널 통합 주문
 */
@Getter
@Builder
public class SellerChannelOrderDto {

    /** 주문 식별자 (Channel_Order.order_id) */
    private String id;

    /** 주문 채널 (예: SHOPIFY, AMAZON) */
    private String channel;

    /** 채널 주문 번호 */
    private String channelOrderNo;

    /** CONK 주문 번호 (order_id) */
    private String conkOrderNo;

    /** 수령인 이름 */
    private String recipient;

    /**
     * 상품 요약 문자열
     * 예) "루미에르 앰플 30ml 외 1건"
     * 1건이면 상품명만, 복수면 "{첫번째 상품명} 외 {n}건"
     */
    private String itemsSummary;

    /**
     * 주문 금액 — 상품 가격 데이터가 integration 서비스에 없어 null 반환
     * ORDER 서비스 연동 후 계산 예정
     */
    private Double orderAmount;

    /** 주문 일시 */
    private LocalDateTime orderedAt;

    /**
     * 주문 상태
     * invoiceNo 없음 → NEW
     * invoiceNo 있음, shippedAt 없음 → PROCESSING
     * shippedAt 있음 → SHIPPED
     */
    private String status;
}
