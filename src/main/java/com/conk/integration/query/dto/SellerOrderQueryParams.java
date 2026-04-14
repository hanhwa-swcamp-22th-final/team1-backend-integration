package com.conk.integration.query.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 셀러 주문 목록 조회 쿼리 파라미터를 담는 DTO다.
 */
@Getter
@Builder
public class SellerOrderQueryParams {

    private final String sellerId;

    // 채널 필터 (SHOPIFY 등) — null이면 전체
    private final String channel;

    // 주문번호 또는 수령인명 검색 키워드 — null이면 전체
    private final String search;

    // 0-based 페이지 번호 (기본값 0)
    private final int page;

    // 페이지당 항목 수 (기본값 20)
    private final int size;

    public int getPage() {
        return Math.max(page, 0);
    }

    public int getSize() {
        return size > 0 ? size : 20;
    }

    // MyBatis LIMIT/OFFSET 계산용 오프셋을 반환한다.
    public int getOffset() {
        return getPage() * getSize();
    }
}
