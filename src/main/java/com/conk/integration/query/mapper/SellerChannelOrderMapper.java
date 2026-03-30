package com.conk.integration.query.mapper;

import com.conk.integration.query.dto.SellerChannelOrderQueryResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SellerChannelOrderMapper {

    List<SellerChannelOrderQueryResult> findBySellerIdWithItemSummary(@Param("sellerId") String sellerId);
}
