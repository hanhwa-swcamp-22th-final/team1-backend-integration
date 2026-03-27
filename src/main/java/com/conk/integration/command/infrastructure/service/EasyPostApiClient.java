package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.infrastructure.config.EasyPostProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EasyPostApiClient {

    private final RestTemplate restTemplate;
    private final EasyPostProperties properties;

    public EasyPostShipmentResponse createShipment(EasyPostCreateShipmentRequest request) {
        HttpEntity<EasyPostCreateShipmentRequest> entity = new HttpEntity<>(request, buildAuthHeaders());
        return restTemplate.exchange(
                properties.getShipmentsUrl(),
                HttpMethod.POST,
                entity,
                EasyPostShipmentResponse.class
        ).getBody();
    }

    public EasyPostShipmentResponse buyRate(String shipmentId, String rateId) {
        Map<String, Object> body = Map.of("rate", Map.of("id", rateId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildAuthHeaders());
        return restTemplate.exchange(
                properties.getBuyRateUrl(shipmentId),
                HttpMethod.POST,
                entity,
                EasyPostShipmentResponse.class
        ).getBody();
    }

    private HttpHeaders buildAuthHeaders() {
        String credentials = properties.getApiKey() + ":";
        String encoded = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
