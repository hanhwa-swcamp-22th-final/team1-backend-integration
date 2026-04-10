package com.conk.integration.command.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShopifyProperties 테스트")
class ShopifyPropertiesTest {

    private final ShopifyProperties properties = new ShopifyProperties();

    @Test
    @DisplayName("유효한 Shopify storeName이 주어지면 기본 URL을 조회했을 때 myshopify 도메인을 반환해야 한다")
    void getBaseUrl_returnsMyShopifyDomainForValidStoreName() {
        assertThat(properties.getBaseUrl("johns-apparel"))
                .isEqualTo("https://johns-apparel.myshopify.com");
    }

    @Test
    @DisplayName("대문자와 앞뒤 공백이 포함된 storeName이 주어지면 기본 URL을 조회했을 때 소문자로 정규화된 myshopify 도메인을 반환해야 한다")
    void getBaseUrl_normalizesStoreName() {
        assertThat(properties.getBaseUrl("  ConkTest  "))
                .isEqualTo("https://conktest.myshopify.com");
    }

    @Test
    @DisplayName("전체 URL이 주어지면 기본 URL을 조회했을 때 예외를 발생시켜야 한다")
    void getBaseUrl_throwsForFullUrlInput() {
        assertThatThrownBy(() -> properties.getBaseUrl("https://evil.example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 Shopify storeName 형식입니다");
    }

    @Test
    @DisplayName("점이 포함된 storeName이 주어지면 기본 URL을 조회했을 때 예외를 발생시켜야 한다")
    void getBaseUrl_throwsForDotInStoreName() {
        assertThatThrownBy(() -> properties.getBaseUrl("evil.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("슬래시가 포함된 storeName이 주어지면 기본 URL을 조회했을 때 예외를 발생시켜야 한다")
    void getBaseUrl_throwsForSlashInStoreName() {
        assertThatThrownBy(() -> properties.getBaseUrl("abc/def"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
