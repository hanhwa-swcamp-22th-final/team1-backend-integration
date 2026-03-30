package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.application.dto.response.ShopifyFulfillmentResponse;
import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    // 토큰과 JSON content-type을 포함한 Shopify 요청 헤더다.
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", properties.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
