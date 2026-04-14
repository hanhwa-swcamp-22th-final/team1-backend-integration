package com.conk.integration.query.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * INT-002 응답 DTO - 셀러 채널 통합 주문 페이지
 */
@Getter
@Builder
public class SellerChannelOrderPageDto {

    private List<SellerChannelOrderDto> items;
    private int total;
    private int page;
    private int size;
}
