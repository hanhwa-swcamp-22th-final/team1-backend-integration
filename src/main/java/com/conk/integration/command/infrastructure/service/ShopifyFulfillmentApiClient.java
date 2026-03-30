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

@Service
@RequiredArgsConstructor
public class ShopifyFulfillmentApiClient {

    private final RestTemplate restTemplate;
    private final ShopifyProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", properties.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
