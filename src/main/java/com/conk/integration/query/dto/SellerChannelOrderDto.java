package com.conk.integration.query.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * INT-002 응답 DTO — 셀러 채널 통합 주문
 */
@Getter
@Builder
public class SellerChannelOrderDto {

    // 화면에서 직접 쓰는 표시용 주문 필드들만 노출한다.
    private String id;
    private String channel;
    private String channelOrderNo;
    private String conkOrderNo;
    private String recipient;
    private String itemsSummary;
    private Double orderAmount;
    private LocalDateTime orderedAt;
    private String status;
}
