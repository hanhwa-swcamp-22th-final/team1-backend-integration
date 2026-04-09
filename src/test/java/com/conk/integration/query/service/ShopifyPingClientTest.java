package com.conk.integration.query.service;

import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Shopify ping 클라이언트의 요청 형식, 응답 해석, 예외 무전파를 검증한다.
@DisplayName("ShopifyPingClient 단위 테스트")
class ShopifyPingClientTest {

    private MockRestServiceServer mockServer;
    private ShopifyPingClient client;

    private static final String STORE_NAME = "conktest";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String API_VERSION = "2025-01";

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        ShopifyProperties properties = new ShopifyProperties();
        properties.setApiVersion(API_VERSION);

        client = new ShopifyPingClient(restTemplate, properties);
    }

    // ─────────────────────────────────────────────────────────
    // 요청 형식 검증
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] GraphQL 엔드포인트로 POST 요청을 전송한다")
    void ping_postsToGraphQLUrl() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.ping(STORE_NAME, ACCESS_TOKEN);
        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] X-Shopify-Access-Token 헤더를 포함한다")
    void ping_includesAccessTokenHeader() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(header("X-Shopify-Access-Token", ACCESS_TOKEN))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.ping(STORE_NAME, ACCESS_TOKEN);
        mockServer.verify();
    }

    // ─────────────────────────────────────────────────────────
    // 응답 해석
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] 200 응답 → true 반환")
    void ping_returns_true_on200() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withSuccess("{\"data\":{\"shop\":{\"id\":\"gid://shopify/Shop/1\"}}}", MediaType.APPLICATION_JSON));

        assertThat(client.ping(STORE_NAME, ACCESS_TOKEN)).isTrue();
    }

    @Test
    @DisplayName("[GREEN] 401 Unauthorized → false 반환 (예외 전파 없음)")
    void ping_returns_false_on401() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.ping(STORE_NAME, ACCESS_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("[GREEN] 403 Forbidden → false 반환 (예외 전파 없음)")
    void ping_returns_false_on403() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client.ping(STORE_NAME, ACCESS_TOKEN)).isFalse();
    }

    @Test
    @DisplayName("[GREEN] 500 서버 오류 → false 반환 (예외 전파 없음)")
    void ping_returns_false_on500() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withServerError());

        assertThat(client.ping(STORE_NAME, ACCESS_TOKEN)).isFalse();
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private String graphqlUrl() {
        return "https://" + STORE_NAME + ".myshopify.com/admin/api/" + API_VERSION + "/graphql.json";
    }
}
