package com.conk.integration.query.dto;

import lombok.Data;

// sellerId 기준으로 조회한 Shopify 스토어 자격증명 DTO다.
@Data
public class ShopifyCredentialDto {

    private String storeName;
    private String accessToken;
}
