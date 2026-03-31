package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderResponse;
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

import java.util.List;
import java.util.Map;

// Shopify GraphQL Admin API로 주문 목록을 조회한다.
@Service
@RequiredArgsConstructor
public class ShopifyOrderClient {

    private static final String ORDERS_QUERY = """
            {
              orders(first: 250) {
                edges {
                  node {
                    id
                    name
                    email
                    createdAt
                    shippingAddress {
                      name
                      firstName
                      lastName
                      phone
                      address1
                      address2
                      city
                      provinceCode
                      zip
                      countryCode
                    }
                    fulfillmentOrders(first: 1) {
                      edges {
                        node {
                          id
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    private final RestTemplate restTemplate;
    private final ShopifyProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Shopify GraphQL로 주문 목록과 fulfillmentOrder ID를 한 번에 조회한다.
    public List<ShopifyOrderResponse.OrderNode> getOrders(String storeName, String accessToken) {
        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("query", ORDERS_QUERY));
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildHeaders(accessToken));

            ShopifyOrderResponse response = restTemplate.exchange(
                    properties.getGraphQLUrl(storeName),
                    HttpMethod.POST,
                    entity,
                    ShopifyOrderResponse.class
            ).getBody();

            if (response == null || response.getData() == null
                    || response.getData().getOrders() == null) {
                throw new IllegalStateException("Shopify GraphQL API returned empty response");
            }

            return response.getData().getOrders().getEdges().stream()
                    .map(ShopifyOrderResponse.OrderEdge::getNode)
                    .toList();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Shopify GraphQL request", e);
        }
    }

    // 토큰과 JSON content-type을 포함한 Shopify 요청 헤더다.
    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
