package com.conk.integration.command.application.service;

import com.conk.integration.common.channel.dto.ShopifyCredentialDto;

/**
 * command 애플리케이션 서비스가 채널 자격증명을 읽기 위해 사용하는 계약이다.
 */
public interface ShopifyCredentialReader {

    /**
     * sellerId 기준으로 Shopify 자격증명을 조회한다.
     *
     * @param sellerId 셀러 식별자
     * @return Shopify 자격증명 DTO
     */
    ShopifyCredentialDto findShopifyCredential(String sellerId);
}
