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
                    lineItems(first: 50) {
                      edges {
                        node {
                          sku
                          title
                          quantity
                          variant {
                            id
                          }
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

    /**
     * Shopify GraphQL로 주문 목록과 fulfillmentOrder ID를 한 번에 조회한다.
     *
     * @param storeName   Shopify 스토어명
     * @param accessToken Shopify Admin API 액세스 토큰
     * @return 주문 노드 목록
     */
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
                throw new IllegalStateException("Shopify GraphQL API 응답이 비어있습니다");
            }

            return response.getData().getOrders().getEdges().stream()
                    .map(ShopifyOrderResponse.OrderEdge::getNode)
                    .toList();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Shopify GraphQL 요청을 직렬화하는데 실패했습니다", e);
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
