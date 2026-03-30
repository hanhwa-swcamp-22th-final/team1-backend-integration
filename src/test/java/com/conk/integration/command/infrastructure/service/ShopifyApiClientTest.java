package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderDto;
import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// ShopifyApiClient가 주문 조회 URL과 헤더를 올바르게 만드는지 검증한다.
@DisplayName("ShopifyApiClient Tests")
class ShopifyApiClientTest {

    private MockRestServiceServer server;
    private ShopifyApiClient client;

    @BeforeEach
    void setUp() {
        // 외부 Shopify 호출은 MockRestServiceServer로 고정해 순수 HTTP 계약만 본다.
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);

        ShopifyProperties properties = new ShopifyProperties();
        properties.setStoreName("test-store");
        properties.setAccessToken("test-access-token");
        properties.setApiVersion("2025-01");

        client = new ShopifyApiClient(restTemplate, properties);
    }

    // ─────────────────────────────────────────────────────────
    // Cycle 1: 주문 목록 조회
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[RED→GREEN] 주문 목록 조회 - 1건 파싱 성공")
    void getOrders_returnsParsedOrders_whenSuccessful() {
        // given
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess(
                      """
                      {
                        "orders": [
                          {
                            "id": 4502818226334,
                            "name": "#1001",
                            "email": "customer@example.com",
                            "created_at": "2025-01-15T10:00:00-05:00",
                            "financial_status": "paid",
                            "fulfillment_status": null,
                            "shipping_address": {
                              "name": "John Doe",
                              "first_name": "John",
                              "last_name": "Doe",
                              "phone": "555-1234",
                              "address1": "123 Main St",
                              "address2": "Apt 4B",
                              "city": "New York",
                              "province_code": "NY",
                              "zip": "10001",
                              "country_code": "US"
                            }
                          }
                        ]
                      }
                      """,
                      MediaType.APPLICATION_JSON));

        // when
        List<ShopifyOrderDto> orders = client.getOrders();

        // then
        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().getId()).isEqualTo(4502818226334L);
        assertThat(orders.getFirst().getName()).isEqualTo("#1001");
        assertThat(orders.getFirst().getFinancialStatus()).isEqualTo("paid");
        assertThat(orders.getFirst().getShippingAddress().getName()).isEqualTo("John Doe");
        assertThat(orders.getFirst().getShippingAddress().getCity()).isEqualTo("New York");
        assertThat(orders.getFirst().getShippingAddress().getProvinceCode()).isEqualTo("NY");
        server.verify();
    }

    @Test
    @DisplayName("[RED→GREEN] 주문 목록 조회 - 빈 목록 반환")
    void getOrders_returnsEmptyList_whenNoOrders() {
        // given
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess(
                      "{\"orders\": []}",
                      MediaType.APPLICATION_JSON));

        // when
        List<ShopifyOrderDto> orders = client.getOrders();

        // then
        assertThat(orders).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("[RED→GREEN] 요청에 X-Shopify-Access-Token 헤더 포함")
    void getOrders_includesAccessTokenHeader() {
        // given
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("X-Shopify-Access-Token", "test-access-token"))
              .andRespond(withSuccess(
                      "{\"orders\": []}",
                      MediaType.APPLICATION_JSON));

        // when
        client.getOrders();

        // then
        server.verify();
    }

    @Test
    @DisplayName("[RED→GREEN] 요청 URL에 스토어명과 api-version 포함")
    void getOrders_usesCorrectStoreUrl() {
        // storeName/apiVersion 조합이 잘못되면 실제 API 호출이 모두 실패한다.
        // given
        server.expect(requestTo(
                      org.hamcrest.Matchers.containsString("test-store.myshopify.com/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess(
                      "{\"orders\": []}",
                      MediaType.APPLICATION_JSON));

        // when
        client.getOrders();

        // then
        server.verify();
    }

    @Test
    @DisplayName("[RED→GREEN] 복수 주문 파싱")
    void getOrders_returnsMultipleOrders() {
        // 목록 응답에서 여러 주문이 순서대로 파싱되는지 확인한다.
        // given
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess(
                      """
                      {
                        "orders": [
                          {"id": 1001, "name": "#1001", "created_at": "2025-01-10T00:00:00-05:00"},
                          {"id": 1002, "name": "#1002", "created_at": "2025-01-11T00:00:00-05:00"},
                          {"id": 1003, "name": "#1003", "created_at": "2025-01-12T00:00:00-05:00"}
                        ]
                      }
                      """,
                      MediaType.APPLICATION_JSON));

        // when
        List<ShopifyOrderDto> orders = client.getOrders();

        // then
        assertThat(orders).hasSize(3);
        assertThat(orders).extracting(ShopifyOrderDto::getName)
                          .containsExactly("#1001", "#1002", "#1003");
        server.verify();
    }

    // ─────────────────────────────────────────────────────────
    // Cycle 2: 예외 상황
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] 401 Unauthorized - 잘못된 Access Token이면 HttpClientErrorException 발생")
    void getOrders_throws_whenUnauthorized() {
        // given
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> client.getOrders())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        server.verify();
    }

    @Test
    @DisplayName("[예외] 404 Not Found - 잘못된 스토어명 또는 API 버전이면 HttpClientErrorException 발생")
    void getOrders_throws_whenStoreNotFound() {
        // given
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> client.getOrders())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        server.verify();
    }

    @Test
    @DisplayName("[예외] 429 Too Many Requests - Rate Limit 초과 시 HttpClientErrorException 발생")
    void getOrders_throws_whenRateLimitExceeded() {
        // given
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when & then
        assertThatThrownBy(() -> client.getOrders())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        server.verify();
    }

    @Test
    @DisplayName("[예외] 500 Internal Server Error - Shopify 서버 에러 시 HttpServerErrorException 발생")
    void getOrders_throws_whenServerError() {
        // given
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/admin/api/2025-01/orders.json")))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when & then
        assertThatThrownBy(() -> client.getOrders())
                .isInstanceOf(HttpServerErrorException.class)
                .satisfies(e -> assertThat(((HttpServerErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
        server.verify();
    }
}
