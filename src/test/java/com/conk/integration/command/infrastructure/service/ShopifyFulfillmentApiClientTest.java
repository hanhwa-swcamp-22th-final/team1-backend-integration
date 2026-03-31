package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.application.dto.response.ShopifyFulfillmentResponse;
import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import com.conk.integration.query.dto.FulfillmentTargetDto;
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

// Shopify 출고 API 클라이언트의 URL, 헤더, 응답 파싱을 검증한다.
@DisplayName("ShopifyFulfillmentApiClient 단위 테스트")
class ShopifyFulfillmentApiClientTest {

    private MockRestServiceServer mockServer;
    private ShopifyFulfillmentApiClient client;

    private static final String STORE_NAME = "conktest";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String API_VERSION = "2025-01";
    private static final String SHOPIFY_ORDER_ID = "5678901234";

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        ShopifyProperties properties = new ShopifyProperties();
        properties.setStoreName(STORE_NAME);
        properties.setAccessToken(ACCESS_TOKEN);
        properties.setApiVersion(API_VERSION);

        client = new ShopifyFulfillmentApiClient(restTemplate, properties);
    }

    // ─────────────────────────────────────────────────────────
    // createFulfillment() — 단건 REST
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] fulfillment 생성 성공 - fulfillment id 반환")
    void createFulfillment_returnsFulfillmentId_whenSuccessful() {
        mockServer.expect(requestTo(fulfillmentsUrl(SHOPIFY_ORDER_ID)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fulfillmentResponseJson("98765"), MediaType.APPLICATION_JSON));

        ShopifyFulfillmentResponse response = client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest());

        assertThat(response.getFulfillment().getId()).isEqualTo(98765L);
        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] X-Shopify-Access-Token 헤더 포함 확인")
    void createFulfillment_includesAccessTokenHeader() {
        mockServer.expect(requestTo(fulfillmentsUrl(SHOPIFY_ORDER_ID)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Shopify-Access-Token", ACCESS_TOKEN))
                .andRespond(withSuccess(fulfillmentResponseJson("11111"), MediaType.APPLICATION_JSON));

        client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] URL에 orderId가 올바르게 포함된다")
    void createFulfillment_usesCorrectUrlWithOrderId() {
        String anotherOrderId = "9999999999";
        mockServer.expect(requestTo(fulfillmentsUrl(anotherOrderId)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fulfillmentResponseJson("22222"), MediaType.APPLICATION_JSON));

        client.createFulfillment(anotherOrderId, buildRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("[예외] 401 Unauthorized → HttpClientErrorException")
    void createFulfillment_throws_whenUnauthorized() {
        mockServer.expect(requestTo(fulfillmentsUrl(SHOPIFY_ORDER_ID)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("[예외] 422 Unprocessable Entity → HttpClientErrorException")
    void createFulfillment_throws_whenUnprocessableEntity() {
        mockServer.expect(requestTo(fulfillmentsUrl(SHOPIFY_ORDER_ID)))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("[예외] 500 서버 오류 → HttpServerErrorException")
    void createFulfillment_throws_whenServerError() {
        mockServer.expect(requestTo(fulfillmentsUrl(SHOPIFY_ORDER_ID)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.createFulfillment(SHOPIFY_ORDER_ID, buildRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // ─────────────────────────────────────────────────────────
    // createBulkFulfillment() — 다건 GraphQL
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] GraphQL 엔드포인트로 POST 요청을 전송한다")
    void createBulkFulfillment_postsToGraphQLUrl() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Shopify-Access-Token", ACCESS_TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        client.createBulkFulfillment(List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "TRACK-A", "USPS")
        ));

        mockServer.verify();
    }

    @Test
    @DisplayName("[GREEN] 여러 건의 target이 있어도 1회 호출로 처리된다")
    void createBulkFulfillment_sendsOneRequestForMultipleTargets() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        client.createBulkFulfillment(List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "TRACK-A", "UPS"),
                buildTarget("ORD-B", "gid://shopify/FulfillmentOrder/2", "TRACK-B", "USPS"),
                buildTarget("ORD-C", "gid://shopify/FulfillmentOrder/3", "TRACK-C", "FEDEX")
        ));

        // MockRestServiceServer는 등록된 횟수만큼만 허용하므로 1번만 호출됨을 verify로 확인한다.
        mockServer.verify();
    }

    @Test
    @DisplayName("[예외] 빈 타겟 리스트로도 GraphQL 엔드포인트에 요청을 전송한다")
    void createBulkFulfillment_withEmptyTargetList_stillCallsGraphQL() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        client.createBulkFulfillment(List.of());

        mockServer.verify();
    }

    @Test
    @DisplayName("[예외] GraphQL 엔드포인트 401 → HttpClientErrorException")
    void createBulkFulfillment_throws_whenUnauthorized() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.createBulkFulfillment(List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "TRACK-A", "USPS"))))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("[예외] GraphQL 엔드포인트 500 → HttpServerErrorException")
    void createBulkFulfillment_throws_whenServerError() {
        mockServer.expect(requestTo(graphqlUrl()))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.createBulkFulfillment(List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "TRACK-A", "USPS"))))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private String fulfillmentsUrl(String orderId) {
        return "https://" + STORE_NAME + ".myshopify.com/admin/api/" + API_VERSION
                + "/orders/" + orderId + "/fulfillments.json";
    }

    private String graphqlUrl() {
        return "https://" + STORE_NAME + ".myshopify.com/admin/api/" + API_VERSION + "/graphql.json";
    }

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

    private FulfillmentTargetDto buildTarget(String orderId, String fulfillmentOrderId,
                                             String invoiceNo, String carrierType) {
        FulfillmentTargetDto dto = new FulfillmentTargetDto();
        dto.setOrderId(orderId);
        dto.setFulfillmentOrderId(fulfillmentOrderId);
        dto.setInvoiceNo(invoiceNo);
        dto.setCarrierType(carrierType);
        return dto;
    }

    private String fulfillmentResponseJson(String id) {
        return "{\"fulfillment\":{\"id\":" + id + ",\"status\":\"success\","
                + "\"tracking_number\":\"1Z999AA10123456784\",\"tracking_company\":\"UPS\"}}";
    }
}
