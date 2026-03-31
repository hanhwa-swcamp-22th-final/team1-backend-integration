package com.conk.integration.query.service;

import com.conk.integration.query.dto.ShopifyCredentialDto;
import com.conk.integration.query.mapper.ChannelApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 채널 API 자격증명을 query side에서 조회하는 서비스다.
@Service
@RequiredArgsConstructor
public class ChannelApiQueryService {

    private final ChannelApiMapper channelApiMapper;

    /**
     * sellerId로 Shopify 자격증명을 조회한다.
     *
     * @param sellerId 셀러 식별자
     * @return storeName + accessToken이 담긴 자격증명 DTO
     * @throws IllegalStateException 자격증명이 등록되지 않은 경우
     */
    public ShopifyCredentialDto findShopifyCredential(String sellerId) {
        ShopifyCredentialDto cred = channelApiMapper.findShopifyCredential(sellerId);
        if (cred == null) {
            throw new IllegalStateException("Shopify 자격증명을 찾을 수 없습니다: sellerId=" + sellerId);
        }
        return cred;
    }
}
