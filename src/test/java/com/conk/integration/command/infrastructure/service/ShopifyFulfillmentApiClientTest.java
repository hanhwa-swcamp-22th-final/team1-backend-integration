package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.application.dto.response.ShopifyFulfillmentResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// Shopify 출고 API 클라이언트의 URL, 헤더, 응답 파싱을 검증한다.
@DisplayName("ShopifyFulfillmentApiClient 단위 테스트")
class ShopifyFulfillmentApiClientTest {

    private MockRestServiceServer mockServer;
    private ShopifyFulfillmentApiClient client;
    private ShopifyProperties properties;

    private static final String STORE_NAME = "conktest";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String API_VERSION = "2025-01";
    private static final String SHOPIFY_ORDER_ID = "5678901234";

    @BeforeEach
    void setUp() {
        // 실제 Shopify 호출 없이 요청 스펙만 검증하기 위한 서버다.
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        properties = new ShopifyProperties();
        properties.setStoreName(STORE_NAME);
        properties.setAccessToken(ACCESS_TOKEN);
        properties.setApiVersion(API_VERSION);

        client = new ShopifyFulfillmentApiClient(restTemplate, properties);
    }

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] fulfillment 생성 성공 - fulfillment id 반환")
    void createFulfillment_returnsFulfillmentId_whenSuccessful() {
        mockServer.expect(requestTo(expectedUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fulfillmentResponseJson("98765", "success"), MediaType.APPLICATION_JSON));

        ShopifyFulfillmentResponse response = client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest());

        assertThat(response).isNotNull();
        assertThat(response.getFulfillment()).isNotNull();
        assertThat(response.getFulfillment().getId()).isEqualTo(98765L);
        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] X-Shopify-Access-Token 헤더 포함 확인")
    void createFulfillment_includesAccessTokenHeader() {
        mockServer.expect(requestTo(expectedUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Shopify-Access-Token", ACCESS_TOKEN))
                .andRespond(withSuccess(fulfillmentResponseJson("11111", "success"), MediaType.APPLICATION_JSON));

        client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] URL에 orderId 올바르게 포함")
    void createFulfillment_usesCorrectUrlWithOrderId() {
        String anotherOrderId = "9999999999";
        String expectedUrl = "https://" + STORE_NAME + ".myshopify.com/admin/api/" + API_VERSION
                + "/orders/" + anotherOrderId + "/fulfillments.json";

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fulfillmentResponseJson("22222", "success"), MediaType.APPLICATION_JSON));

        client.createFulfillment(anotherOrderId, buildRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] trackingNumber와 company가 요청 body에 포함")
    void createFulfillment_sendsTrackingNumberAndCompany() {
        // 출고 API는 추적번호/운송사 정보가 핵심이므로 body 직렬화만 집중해서 본다.
        mockServer.expect(requestTo(expectedUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(fulfillmentResponseJson("33333", "success"), MediaType.APPLICATION_JSON));

        ShopifyFulfillmentRequest req = ShopifyFulfillmentRequest.builder()
                .fulfillment(ShopifyFulfillmentRequest.FulfillmentBody.builder()
                        .trackingInfo(ShopifyFulfillmentRequest.TrackingInfo.builder()
                                .number("1Z999AA10123456784")
                                .company("UPS")
                                .build())
                        .notifyCustomer(true)
                        .build())
                .build();

        ShopifyFulfillmentResponse response = client.createFulfillment(SHOPIFY_ORDER_ID, req);

        assertThat(response.getFulfillment().getStatus()).isEqualTo("success");
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] 401 Unauthorized → HttpClientErrorException")
    void createFulfillment_throws_whenUnauthorized() {
        mockServer.expect(requestTo(expectedUrl()))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("[예외] 422 Unprocessable Entity → HttpClientErrorException (이미 fulfilled 등)")
    void createFulfillment_throws_whenUnprocessableEntity() {
        mockServer.expect(requestTo(expectedUrl()))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("[예외] 500 서버 오류 → HttpServerErrorException")
    void createFulfillment_throws_whenServerError() {
        mockServer.expect(requestTo(expectedUrl()))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    // 주문별 출고 엔드포인트 URL 조합을 한 곳에서 재사용한다.
    private String expectedUrl() {
        return "https://" + STORE_NAME + ".myshopify.com/admin/api/" + API_VERSION
                + "/orders/" + SHOPIFY_ORDER_ID + "/fulfillments.json";
    }

    // 운송사/추적번호를 포함한 최소 fulfillment 요청 fixture다.
    private ShopifyFulfillmentRequest buildRequest() {
        return ShopifyFulfillmentRequest.builder()
                .fulfillment(ShopifyFulfillmentRequest.FulfillmentBody.builder()
                        .trackingInfo(ShopifyFulfillmentRequest.TrackingInfo.builder()
                                .number("1Z999AA10123456784")
                                .company("UPS")
                                .build())
                        .notifyCustomer(true)
                        .build())
                .build();
    }

    // fulfillment 응답 파싱 테스트에 재사용하는 JSON fixture다.
    private String fulfillmentResponseJson(String id, String status) {
        return "{\"fulfillment\":{\"id\":" + id + ",\"status\":\"" + status + "\","
                + "\"tracking_number\":\"1Z999AA10123456784\",\"tracking_company\":\"UPS\"}}";
    }
}
