package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.application.dto.response.ShopifyFulfillmentResponse;
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

    // 주문별 fulfillment 생성 요청을 전송한다.
    public ShopifyFulfillmentResponse createFulfillment(String shopifyOrderId, ShopifyFulfillmentRequest request) {
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildHeaders());
            return restTemplate.exchange(
                    properties.getFulfillmentsUrl(shopifyOrderId),
                    HttpMethod.POST,
                    entity,
                    ShopifyFulfillmentResponse.class
            ).getBody();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Shopify fulfillment request", e);
        }
    }

    // 여러 주문의 fulfillment를 GraphQL aliased mutation 1회 호출로 일괄 전송한다.
    public void createBulkFulfillment(List<FulfillmentTargetDto> targets) {
        String mutation = buildBulkMutation(targets);
        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("query", mutation));
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildHeaders());
            restTemplate.exchange(
                    properties.getGraphQLUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Shopify GraphQL bulk fulfillment request", e);
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
                    resolveCarrierCompany(t.getCarrierType())
            ));
        }
        sb.append("}");
        return sb.toString();
    }

    // 운송사 문자열을 Shopify가 인식하는 표기로 변환한다.
    private String resolveCarrierCompany(String carrierType) {
        if (carrierType == null) return "USPS";
        return switch (carrierType) {
            case "UPS" -> "UPS";
            case "FEDEX" -> "FedEx";
            default -> "USPS";
        };
    }

    // 토큰과 JSON content-type을 포함한 Shopify 요청 헤더다.
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", properties.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
