package com.conk.integration.query.mapper;

import com.conk.integration.query.dto.SellerChannelCardDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 셀러별 채널 카드 조회용 MyBatis 매퍼다.
@Mapper
public interface SellerChannelCardMapper {

    // 채널별 묶음 통계와 마지막 동기화 시각을 함께 조회한다.
    List<SellerChannelCardDto> findBySellerIdGroupedByChannel(@Param("sellerId") String sellerId);
}
