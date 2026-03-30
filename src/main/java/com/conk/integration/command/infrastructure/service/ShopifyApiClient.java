package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderDto;
import com.conk.integration.command.application.dto.response.ShopifyOrderListResponse;
import com.conk.integration.command.infrastructure.config.ShopifyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

// Shopify Admin API에서 주문 목록을 읽어오는 클라이언트다.
@Service
@RequiredArgsConstructor
public class ShopifyApiClient {

    private final RestTemplate restTemplate;
    private final ShopifyProperties properties;

    /**
     * Shopify Admin API 주문 목록 조회
     * GET https://{store}.myshopify.com/admin/api/{version}/orders.json
     *
     * 인증: X-Shopify-Access-Token 헤더
     */
    public List<ShopifyOrderDto> getOrders() {
        // 상태/limit 파라미터를 고정해 한 번에 최대한 많은 주문을 읽어온다.
        String url = UriComponentsBuilder.fromHttpUrl(properties.getOrdersUrl())
                .queryParam("status", "any")
                .queryParam("limit", 250)
                .toUriString();

        // Shopify Admin API는 토큰을 전용 헤더로 받는다.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", properties.getAccessToken());

        ResponseEntity<ShopifyOrderListResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), ShopifyOrderListResponse.class);

        ShopifyOrderListResponse body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Shopify API returned empty response");
        }
        // null-safe getter를 통해 서비스는 빈 리스트 처리를 단순하게 가져간다.
        return body.getOrders();
    }
}
