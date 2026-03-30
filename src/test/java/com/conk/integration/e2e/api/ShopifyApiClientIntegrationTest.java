package com.conk.integration.e2e.api;
import com.conk.integration.command.infrastructure.service.ShopifyApiClient;


import com.conk.integration.command.application.dto.response.ShopifyOrderDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shopify API 통합 테스트 (개발 스토어)
 *
 * 실행 방법: ./gradlew sandboxTest
 *
 * 실행 전 application-dev.yml에 실제 개발 스토어 정보 입력 필요:
 *   - shopify.store-name  (예: my-dev-store)
 *   - shopify.access-token (Shopify Admin API Access Token)
 */
@Tag("sandbox")
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Shopify API 통합 테스트 (개발 스토어)")
class ShopifyApiClientIntegrationTest {

    @Autowired
    private ShopifyApiClient shopifyApiClient;

    // 개발 스토어에서 실제 주문 목록을 읽고 최소 필드가 채워지는지 확인한다.
    @Test
    @DisplayName("주문 목록 조회 - 실제 개발 스토어 API 호출")
    void getOrders_returnsOrdersFromDevStore() {
        // when
        List<ShopifyOrderDto> orders = shopifyApiClient.getOrders();

        // then
        assertThat(orders).isNotNull();
        System.out.println("[Shopify Dev Store] 조회된 주문 수: " + orders.size());

        orders.forEach(order -> {
            System.out.println("  - OrderId: " + order.getId()
                    + ", Name: " + order.getName()
                    + ", Status: " + order.getFinancialStatus()
                    + ", CreatedAt: " + order.getCreatedAt());
            assertThat(order.getId()).isNotNull();
            assertThat(order.getName()).isNotBlank();
        });
    }
}
