package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderResponse;
import com.conk.integration.command.infrastructure.config.ShopifyProperties;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// Shopify GraphQL 주문 조회 클라이언트의 URL, 헤더, 응답 파싱을 검증한다.
@DisplayName("ShopifyOrderClient 단위 테스트")
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
    @DisplayName("[GREEN] GraphQL 엔드포인트로 POST 요청을 전송한다")
    void getOrders_postsToGraphQLUrl() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(ordersResponseJson(), MediaType.APPLICATION_JSON));

        client.getOrders(STORE_NAME, ACCESS_TOKEN);
        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] X-Shopify-Access-Token 헤더를 포함한다")
    void getOrders_includesAccessTokenHeader() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(header("X-Shopify-Access-Token", ACCESS_TOKEN))
                .andRespond(withSuccess(ordersResponseJson(), MediaType.APPLICATION_JSON));

        client.getOrders(STORE_NAME, ACCESS_TOKEN);
        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] 응답에서 OrderNode 목록을 정확히 파싱한다")
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
    @DisplayName("[GREEN] fulfillmentOrders GID가 응답에 포함된다")
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
    @DisplayName("[GREEN] 주문이 없으면 빈 리스트를 반환한다")
    void getOrders_returnsEmptyList_whenNoOrders() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess(emptyOrdersResponseJson(), MediaType.APPLICATION_JSON));

        List<ShopifyOrderResponse.OrderNode> orders = client.getOrders(STORE_NAME, ACCESS_TOKEN);

        assertThat(orders).isEmpty();
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] API body가 null이면 IllegalStateException")
    void getOrders_throwsWhenResponseBodyIsNull() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("응답이 비어있습니다");
    }

    @Test
    @DisplayName("[예외] data 필드가 null이면 IllegalStateException")
    void getOrders_throwsWhenDataIsNull() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess("{\"data\": null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("응답이 비어있습니다");
    }

    @Test
    @DisplayName("[예외] orders 필드가 null이면 IllegalStateException")
    void getOrders_throwsWhenOrdersIsNull() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess("{\"data\": {\"orders\": null}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("응답이 비어있습니다");
    }

    @Test
    @DisplayName("[예외] 401 Unauthorized → HttpClientErrorException")
    void getOrders_throws_whenUnauthorized() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getOrders(STORE_NAME, ACCESS_TOKEN))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("[예외] 500 서버 오류 → HttpServerErrorException")
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
}
