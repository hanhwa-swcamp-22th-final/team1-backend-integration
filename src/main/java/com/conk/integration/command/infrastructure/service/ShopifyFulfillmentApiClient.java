package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.application.dto.response.ShopifyFulfillmentResponse;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import com.conk.integration.query.dto.FulfillmentTargetDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

// Shopify fulfillment 생성 API 호출을 담당한다.
@Service
@RequiredArgsConstructor
public class ShopifyFulfillmentApiClient {

    private final RestTemplate restTemplate;
    private final ShopifyProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 주문별 단건 fulfillment 생성 요청을 전송한다.
     *
     * @param storeName      Shopify 스토어명
     * @param accessToken    Shopify Admin API 액세스 토큰
     * @param shopifyOrderId Shopify 주문 ID
     * @param request        fulfillment 생성 요청 바디
     * @return Shopify fulfillment 응답
     */
    public ShopifyFulfillmentResponse createFulfillment(String storeName, String accessToken,
                                                        String shopifyOrderId, ShopifyFulfillmentRequest request) {
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildHeaders(accessToken));
            return restTemplate.exchange(
                    properties.getFulfillmentsUrl(storeName, shopifyOrderId),
                    HttpMethod.POST,
                    entity,
                    ShopifyFulfillmentResponse.class
            ).getBody();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Shopify fulfillment 요청을 직렬화하는데 실패했습니다", e);
        }
    }

    /**
     * 여러 주문의 fulfillment를 GraphQL aliased mutation 1회 호출로 일괄 전송한다.
     *
     * @param storeName   Shopify 스토어명
     * @param accessToken Shopify Admin API 액세스 토큰
     * @param targets     fulfillment 전송 대상 목록
     */
    public void createBulkFulfillment(String storeName, String accessToken, List<FulfillmentTargetDto> targets) {
        String mutation = buildBulkMutation(targets);
        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("query", mutation));
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildHeaders(accessToken));
            restTemplate.exchange(
                    properties.getGraphQLUrl(storeName),
                    HttpMethod.POST,
                    entity,
                    String.class
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Shopify GraphQL bulk fulfillment 요청을 직렬화하는데 실패했습니다", e);
        }
    }

    // 각 주문을 alias로 구분한 fulfillmentCreate mutation을 동적으로 조합한다.
    private String buildBulkMutation(List<FulfillmentTargetDto> targets) {
        StringBuilder sb = new StringBuilder("mutation {");
        for (int i = 0; i < targets.size(); i++) {
            FulfillmentTargetDto t = targets.get(i);
            sb.append(String.format("""
                     f_%d: fulfillmentCreate(fulfillment: {
                       lineItemsByFulfillmentOrder: [{fulfillmentOrderId: "%s"}]
                       trackingInfo: {number: "%s", company: "%s"}
                       notifyCustomer: true
                     }) {
                       fulfillment { id status }
                       userErrors { field message }
                     }
                    """,
                    i,
                    t.getFulfillmentOrderId(),
                    t.getInvoiceNo(),
                    CarrierType.fromEasyPostName(t.getCarrierType()).toShopifyName()
            ));
        }
        sb.append("}");
        return sb.toString();
    }

    // 토큰과 JSON content-type을 포함한 Shopify 요청 헤더다.
    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
