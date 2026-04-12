package com.conk.integration.common.channel;

/**
 * 외부 Shopify 자격증명의 유효성을 확인하는 계약이다.
 */
public interface ShopifyConnectionVerifier {

    /**
     * 주어진 스토어명과 액세스 토큰으로 Shopify 연결 가능 여부를 확인한다.
     *
     * @param storeName Shopify 스토어명
     * @param accessToken Shopify Admin API 액세스 토큰
     * @return 연결 가능하면 true
     */
    boolean ping(String storeName, String accessToken);
}
