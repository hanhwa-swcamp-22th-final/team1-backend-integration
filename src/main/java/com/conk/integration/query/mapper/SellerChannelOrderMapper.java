package com.conk.integration.query.mapper;

import com.conk.integration.query.dto.SellerChannelOrderQueryResult;
import com.conk.integration.query.dto.SellerOrderQueryParams;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 셀러별 주문 목록 조회용 MyBatis 매퍼다.
@Mapper
public interface SellerChannelOrderMapper {

    // 채널/검색어/페이지 조건을 적용해 주문 기본 정보와 상품 요약을 함께 조회한다.
    List<SellerChannelOrderQueryResult> findBySellerIdWithItemSummary(@Param("params") SellerOrderQueryParams params);

    // 동일한 필터 조건으로 전체 주문 수를 조회한다.
    int countBySellerIdWithFilters(@Param("params") SellerOrderQueryParams params);
}
