package com.conk.integration.query.service;

import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

// Shopify GraphQL API에 경량 ping을 보내 자격증명 유효성을 확인하는 query 전용 클라이언트다.
@Service
@RequiredArgsConstructor
public class ShopifyPingClient {

    // shop.id만 조회하는 최소 쿼리로 네트워크/서버 부담을 최소화한다.
    private static final String PING_QUERY = "{ shop { id } }";

    private final RestTemplate restTemplate;
    private final ShopifyProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Shopify GraphQL API에 경량 ping을 보내 자격증명 유효성을 확인한다.
     *
     * @param storeName   Shopify 스토어명
     * @param accessToken Shopify Admin API 액세스 토큰
     * @return 연결 성공 시 true, HTTP 오류/네트워크 실패 등 모든 예외 시 false
     */
    public boolean ping(String storeName, String accessToken) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("query", PING_QUERY));
            HttpEntity<String> entity = new HttpEntity<>(body, buildHeaders(accessToken));
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getGraphQLUrl(storeName),
                    HttpMethod.POST,
                    entity,
                    String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            // 401/403/5xx/타임아웃 등 모든 실패는 false로 처리한다.
            // 예외를 전파하면 AMAZON 등 다른 채널 카드 전체 응답이 실패하기 때문이다.
            return false;
        }
    }

    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
