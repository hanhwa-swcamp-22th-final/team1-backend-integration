package com.conk.integration.command.infrastructure.client;

import com.conk.integration.command.infrastructure.client.dto.ShopifyOrderSyncRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

// order-service 내부 호출 클라이언트 — application.yml의 order.service.url로 엔드포인트를 조립한다.
@Slf4j
@Component
public class OrderServiceClient {

    private final RestTemplate restTemplate;
    private final String shopifyOrderUrl;

    public OrderServiceClient(
            RestTemplate restTemplate,
            @Value("${order.service.url}") String orderServiceUrl) {
        this.restTemplate = restTemplate;
        this.shopifyOrderUrl = orderServiceUrl + "/orders/seller/shopify";
        log.info("OrderServiceClient 초기화: shopifyOrderUrl={}", this.shopifyOrderUrl);
    }

    public void syncToOrderService(String sellerId, ShopifyOrderSyncRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Seller-Id", sellerId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(
                shopifyOrderUrl,
                new HttpEntity<>(request, headers),
                Void.class);
    }
}
