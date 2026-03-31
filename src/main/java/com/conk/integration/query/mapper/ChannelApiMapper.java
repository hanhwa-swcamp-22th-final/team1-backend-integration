package com.conk.integration.query.mapper;

import com.conk.integration.query.dto.ShopifyCredentialDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 채널 API 자격증명 조회용 MyBatis 매퍼다.
@Mapper
public interface ChannelApiMapper {

    // sellerId에 해당하는 Shopify 스토어 자격증명을 조회한다.
    ShopifyCredentialDto findShopifyCredential(@Param("sellerId") String sellerId);
}
