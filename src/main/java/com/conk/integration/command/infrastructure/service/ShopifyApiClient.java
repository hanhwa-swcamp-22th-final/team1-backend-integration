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
        String url = UriComponentsBuilder.fromHttpUrl(properties.getOrdersUrl())
                .queryParam("status", "any")
                .queryParam("limit", 250)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", properties.getAccessToken());

        ResponseEntity<ShopifyOrderListResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), ShopifyOrderListResponse.class);

        ShopifyOrderListResponse body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Shopify API returned empty response");
        }
        return body.getOrders();
    }
}
