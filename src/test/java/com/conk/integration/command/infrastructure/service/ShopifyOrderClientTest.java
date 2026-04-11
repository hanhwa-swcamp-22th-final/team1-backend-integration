package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderResponse;
import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import com.conk.integration.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// Shopify GraphQL 주문 조회 클라이언트의 URL, 헤더, 응답 파싱을 검증한다.
@DisplayName("ShopifyOrderClient 테스트")
class ShopifyOrderClientTest {

    private MockRestServiceServer mockServer;
    private ShopifyOrderClient client;

    private static final String STORE_NAME = "conktest";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String API_VERSION = "2025-01";

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        ShopifyProperties properties = new ShopifyProperties();
        properties.setApiVersion(API_VERSION);

        client = new ShopifyOrderClient(restTemplate, properties);
    }

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("인증 정보가 주어지면 주문 목록을 조회했을 때 GraphQL 엔드포인트로 POST 요청을 전송해야 한다")
    void getOrders_postsToGraphQLUrl() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(ordersResponseJson(), MediaType.APPLICATION_JSON));

        client.getOrders(STORE_NAME, ACCESS_TOKEN);
        mockServer.verify();
    }

    @Test
    @DisplayName("인증 정보가 주어지면 주문 목록을 조회했을 때 X-Shopify-Access-Token 헤더를 포함해야 한다")
    void getOrders_includesAccessTokenHeader() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(header("X-Shopify-Access-Token", ACCESS_TOKEN))
                .andRespond(withSuccess(ordersResponseJson(), MediaType.APPLICATION_JSON));

        client.getOrders(STORE_NAME, ACCESS_TOKEN);
        mockServer.verify();
    }

    @Test
    @DisplayName("주문 응답이 주어지면 주문 목록을 조회했을 때 OrderNode 목록을 정확히 파싱해야 한다")
    void getOrders_parsesOrderNodesCorrectly() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess(ordersResponseJson(), MediaType.APPLICATION_JSON));

        List<ShopifyOrderResponse.OrderNode> orders = client.getOrders(STORE_NAME, ACCESS_TOKEN);

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getId()).isEqualTo("gid://shopify/Order/1001");
        assertThat(orders.get(0).getName()).isEqualTo("#1001");
        assertThat(orders.get(1).getId()).isEqualTo("gid://shopify/Order/1002");
        assertThat(orders.get(1).getName()).isEqualTo("#1002");
    }

    @Test
    @DisplayName("fulfillmentOrders가 포함된 응답이 주어지면 주문 목록을 조회했을 때 fulfillmentOrders GID를 파싱해야 한다")
    void getOrders_parsesFulfillmentOrderId() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess(ordersResponseJson(), MediaType.APPLICATION_JSON));

        List<ShopifyOrderResponse.OrderNode> orders = client.getOrders(STORE_NAME, ACCESS_TOKEN);

        ShopifyOrderResponse.FulfillmentOrderConnection fo = orders.getFirst().getFulfillmentOrders();
        assertThat(fo).isNotNull();
        assertThat(fo.getEdges()).hasSize(1);
        assertThat(fo.getEdges().getFirst().getNode().getId())
                .isEqualTo("gid://shopify/FulfillmentOrder/9001");
    }

    @Test
    @DisplayName("주문이 없는 응답이 주어지면 주문 목록을 조회했을 때 빈 리스트를 반환해야 한다")
    void getOrders_returnsEmptyList_whenNoOrders() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess(emptyOrdersResponseJson(), MediaType.APPLICATION_JSON));

        List<ShopifyOrderResponse.OrderNode> orders = client.getOrders(STORE_NAME, ACCESS_TOKEN);

        assertThat(orders).isEmpty();
    }

    @Test
    @DisplayName("시작 시각이 주어지면 이후 주문 목록을 조회했을 때 created_at 필터를 포함한 GraphQL 요청을 전송해야 한다")
    void getOrdersSince_includesCreatedAtFilter() {
        LocalDateTime since = LocalDateTime.of(2026, 4, 11, 9, 0);

        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("created_at:>'2026-04-11T09:00Z'")))
                .andRespond(withSuccess(ordersResponseJson(), MediaType.APPLICATION_JSON));

        client.getOrdersSince(STORE_NAME, ACCESS_TOKEN, since);
        mockServer.verify();
    }

    @Test
    @DisplayName("다음 페이지가 있으면 이후 주문 건수를 조회했을 때 모든 페이지를 순회해 전체 건수를 반환해야 한다")
    void countOrdersSince_fetchesAllPages() {
        LocalDateTime since = LocalDateTime.of(2026, 4, 11, 9, 0);

        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("created_at:>'2026-04-11T09:00Z'")))
                .andRespond(withSuccess(pagedOrdersResponseJson(true, "cursor-1", 250), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("after: \\\"cursor-1\\\"")))
                .andRespond(withSuccess(pagedOrdersResponseJson(false, null, 20), MediaType.APPLICATION_JSON));

        int total = client.countOrdersSince(STORE_NAME, ACCESS_TOKEN, since);

        assertThat(total).isEqualTo(270);
        mockServer.verify();
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("API body가 null이면 주문 목록을 조회했을 때 BusinessException(INT-301)을 발생시켜야 한다")
    void getOrders_throwsWhenResponseBodyIsNull() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("data 필드가 null이면 주문 목록을 조회했을 때 BusinessException(INT-301)을 발생시켜야 한다")
    void getOrders_throwsWhenDataIsNull() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess("{\"data\": null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("orders 필드가 null이면 주문 목록을 조회했을 때 BusinessException(INT-301)을 발생시켜야 한다")
    void getOrders_throwsWhenOrdersIsNull() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess("{\"data\": {\"orders\": null}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("401 응답이 주어지면 주문 목록을 조회했을 때 HttpClientErrorException을 전파해야 한다")
    void getOrders_throws_whenUnauthorized() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("500 응답이 주어지면 주문 목록을 조회했을 때 HttpServerErrorException을 전파해야 한다")
    void getOrders_throws_whenServerError() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private String graphqlUrl() {
        return "https://" + STORE_NAME + ".myshopify.com/admin/api/" + API_VERSION + "/graphql.json";
    }

    private String ordersResponseJson() {
        return """
                {
                  "data": {
                    "orders": {
                      "edges": [
                        {
                          "node": {
                            "id": "gid://shopify/Order/1001",
                            "name": "#1001",
                            "email": "a@test.com",
                            "createdAt": "2024-01-15T10:00:00-05:00",
                            "shippingAddress": {
                              "name": "John Doe",
                              "firstName": "John",
                              "lastName": "Doe",
                              "phone": "555-1234",
                              "address1": "123 Main St",
                              "address2": null,
                              "city": "New York",
                              "provinceCode": "NY",
                              "zip": "10001",
                              "countryCode": "US"
                            },
                            "fulfillmentOrders": {
                              "edges": [
                                { "node": { "id": "gid://shopify/FulfillmentOrder/9001" } }
                              ]
                            }
                          }
                        },
                        {
                          "node": {
                            "id": "gid://shopify/Order/1002",
                            "name": "#1002",
                            "email": "b@test.com",
                            "createdAt": "2024-01-16T10:00:00-05:00",
                            "shippingAddress": null,
                            "fulfillmentOrders": { "edges": [] }
                          }
                        }
                      ]
                    }
                  }
                }
                """;
    }

    private String emptyOrdersResponseJson() {
        return """
                {
                  "data": {
                    "orders": {
                      "edges": []
                    }
                  }
                }
                """;
    }

    private String pagedOrdersResponseJson(boolean hasNextPage, String endCursor, int edgeCount) {
        StringBuilder edges = new StringBuilder();
        for (int i = 0; i < edgeCount; i++) {
            if (i > 0) {
                edges.append(",");
            }
            edges.append("""
                    {
                      "node": {
                        "id": "gid://shopify/Order/%d",
                        "name": "#%d",
                        "email": "paged@test.com",
                        "createdAt": "2024-01-15T10:00:00-05:00",
                        "shippingAddress": null,
                        "fulfillmentOrders": { "edges": [] }
                      }
                    }
                    """.formatted(2000 + i, 2000 + i));
        }

        String cursorJson = endCursor == null ? "null" : "\"" + endCursor + "\"";
        return """
                {
                  "data": {
                    "orders": {
                      "edges": [%s],
                      "pageInfo": {
                        "hasNextPage": %s,
                        "endCursor": %s
                      }
                    }
                  }
                }
                """.formatted(edges, hasNextPage, cursorJson);
    }
}
