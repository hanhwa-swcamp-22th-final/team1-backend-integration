package com.conk.integration.query.mapper;

import com.conk.integration.query.dto.SellerChannelOrderQueryResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 셀러별 주문 목록 조회용 MyBatis 매퍼다.
@Mapper
public interface SellerChannelOrderMapper {

    // 주문 기본 정보와 첫 상품/품목 수 요약용 컬럼을 함께 조회한다.
    List<SellerChannelOrderQueryResult> findBySellerIdWithItemSummary(@Param("sellerId") String sellerId);
}
