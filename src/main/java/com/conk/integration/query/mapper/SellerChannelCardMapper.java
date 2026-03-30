package com.conk.integration.query.mapper;

import com.conk.integration.query.dto.SellerChannelCardDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SellerChannelCardMapper {

    List<SellerChannelCardDto> findBySellerIdGroupedByChannel(@Param("sellerId") String sellerId);
}
