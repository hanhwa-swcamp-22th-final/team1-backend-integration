package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderResponse;
import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// Shopify GraphQL Admin API로 주문 목록을 조회한다.
@Service
@RequiredArgsConstructor
public class ShopifyOrderClient {

    private static final String ORDERS_QUERY_TEMPLATE = """
            {
              orders(first: 250%s%s) {
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
                pageInfo {
                  hasNextPage
                  endCursor
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
        return toOrderNodes(fetchOrders(storeName, accessToken, null, null));
    }

    /**
     * Shopify GraphQL로 특정 시각 이후 주문 목록과 fulfillmentOrder ID를 한 번에 조회한다.
     *
     * @param storeName   Shopify 스토어명
     * @param accessToken Shopify Admin API 액세스 토큰
     * @param since       조회 시작 시각(초과 조건)
     * @return 주문 노드 목록
     */
    public List<ShopifyOrderResponse.OrderNode> getOrdersSince(
            String storeName,
            String accessToken,
            LocalDateTime since) {

        return toOrderNodes(fetchOrders(storeName, accessToken, since, null));
    }

    public int countOrdersSince(String storeName, String accessToken, LocalDateTime since) {
        String cursor = null;
        int total = 0;

        do {
            ShopifyOrderResponse response = fetchOrders(storeName, accessToken, since, cursor);
            List<ShopifyOrderResponse.OrderEdge> edges = getOrderEdges(response);
            total += edges.size();

            ShopifyOrderResponse.PageInfo pageInfo = response.getData().getOrders().getPageInfo();
            if (pageInfo == null || !pageInfo.isHasNextPage()) {
                break;
            }
            cursor = pageInfo.getEndCursor();
        } while (cursor != null && !cursor.isBlank());

        return total;
    }

    private ShopifyOrderResponse fetchOrders(
            String storeName,
            String accessToken,
            LocalDateTime since,
            String cursor) {

        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("query", buildOrdersQuery(since, cursor)));
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildHeaders(accessToken));

            ShopifyOrderResponse response = restTemplate.exchange(
                    properties.getGraphQLUrl(storeName),
                    HttpMethod.POST,
                    entity,
                    ShopifyOrderResponse.class
            ).getBody();

            if (response == null || response.getData() == null
                    || response.getData().getOrders() == null) {
                throw new BusinessException(ErrorCode.SHOPIFY_EMPTY_RESPONSE);
            }
            return response;

        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SHOPIFY_SERIALIZATION_FAILED);
        }
    }

    private List<ShopifyOrderResponse.OrderNode> toOrderNodes(ShopifyOrderResponse response) {
        return getOrderEdges(response).stream()
                .map(ShopifyOrderResponse.OrderEdge::getNode)
                .toList();
    }

    private List<ShopifyOrderResponse.OrderEdge> getOrderEdges(ShopifyOrderResponse response) {
        List<ShopifyOrderResponse.OrderEdge> edges = response.getData().getOrders().getEdges();
        return edges == null ? Collections.emptyList() : edges;
    }

    private String buildOrdersQuery(LocalDateTime since, String cursor) {
        String filterClause = since == null ? "" : ", query: \"" + buildCreatedAtFilter(since) + "\"";
        String cursorClause = (cursor == null || cursor.isBlank()) ? "" : ", after: \"" + cursor + "\"";
        return ORDERS_QUERY_TEMPLATE.formatted(filterClause, cursorClause);
    }

    private String buildCreatedAtFilter(LocalDateTime since) {
        return "created_at:>'" + since.atOffset(ZoneOffset.UTC) + "'";
    }

    // 토큰과 JSON content-type을 포함한 Shopify 요청 헤더다.
    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
