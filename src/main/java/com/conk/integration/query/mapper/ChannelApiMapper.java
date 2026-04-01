package com.conk.integration.query.mapper;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.query.dto.ShopifyCredentialDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 채널 API 자격증명 조회용 MyBatis 매퍼다.
@Mapper
public interface ChannelApiMapper {

    // sellerId에 해당하는 Shopify 스토어 자격증명을 조회한다.
    ShopifyCredentialDto findShopifyCredential(@Param("sellerId") String sellerId);

    // 특정 셀러의 모든 채널 API 설정을 조회한다.
    List<ChannelApi> findByIdSellerId(@Param("sellerId") String sellerId);
}
