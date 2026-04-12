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

/**
 * Shopify GraphQL Admin API로 주문 목록을 조회한다.
 */
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
    private final ObjectMapper objectMapper;

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

    /**
     * 특정 시각 이후 생성된 주문 수를 페이지네이션을 따라가며 모두 합산한다.
     *
     * @param storeName Shopify 스토어명
     * @param accessToken Shopify Admin API 액세스 토큰
     * @param since 조회 시작 시각
     * @return since 이후 주문 건수
     */
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

    /**
     * Shopify GraphQL 주문 조회를 실행하고 응답 본문을 DTO로 역직렬화한다.
     *
     * @param storeName Shopify 스토어명
     * @param accessToken Shopify Admin API 액세스 토큰
     * @param since 생성 시각 필터
     * @param cursor 페이지네이션 커서
     * @return Shopify 주문 조회 응답
     * @throws BusinessException 응답 본문이 비어 있거나 직렬화에 실패한 경우
     */
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

    /**
     * GraphQL edges 구조를 주문 노드 목록으로 평탄화한다.
     *
     * @param response Shopify 주문 조회 응답
     * @return 주문 노드 목록
     */
    private List<ShopifyOrderResponse.OrderNode> toOrderNodes(ShopifyOrderResponse response) {
        return getOrderEdges(response).stream()
                .map(ShopifyOrderResponse.OrderEdge::getNode)
                .toList();
    }

    /**
     * 응답에서 주문 edge 목록을 안전하게 추출한다.
     *
     * @param response Shopify 주문 조회 응답
     * @return 주문 edge 목록, 없으면 빈 리스트
     */
    private List<ShopifyOrderResponse.OrderEdge> getOrderEdges(ShopifyOrderResponse response) {
        List<ShopifyOrderResponse.OrderEdge> edges = response.getData().getOrders().getEdges();
        return edges == null ? Collections.emptyList() : edges;
    }

    /**
     * created_at 필터와 after 커서를 반영한 GraphQL 주문 조회 쿼리를 생성한다.
     *
     * @param since 생성 시각 필터
     * @param cursor 페이지네이션 커서
     * @return Shopify 주문 조회 GraphQL 쿼리
     */
    private String buildOrdersQuery(LocalDateTime since, String cursor) {
        String filterClause = since == null ? "" : ", query: \"" + buildCreatedAtFilter(since) + "\"";
        String cursorClause = (cursor == null || cursor.isBlank()) ? "" : ", after: \"" + cursor + "\"";
        return ORDERS_QUERY_TEMPLATE.formatted(filterClause, cursorClause);
    }

    /**
     * Shopify orders query에 사용할 created_at 검색식을 생성한다.
     *
     * @param since 조회 시작 시각
     * @return created_at 검색식 문자열
     */
    private String buildCreatedAtFilter(LocalDateTime since) {
        return "created_at:>'" + since.atOffset(ZoneOffset.UTC) + "'";
    }

    /**
     * 토큰과 JSON content-type을 포함한 Shopify 요청 헤더를 생성한다.
     *
     * @param accessToken Shopify Admin API 액세스 토큰
     * @return Shopify 요청 헤더
     */
    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
