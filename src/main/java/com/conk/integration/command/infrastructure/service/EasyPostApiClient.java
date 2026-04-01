package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.infrastructure.config.EasyPostProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

// EasyPost REST API 호출과 요청/응답 직렬화를 담당한다.
@Service
@RequiredArgsConstructor
public class EasyPostApiClient {

    private final RestTemplate restTemplate;
    private final EasyPostProperties properties;
    private final ObjectMapper objectMapper;  // Spring Bean 주입

    /**
     * EasyPost API를 호출해 shipment를 생성하고 rate 목록을 반환한다.
     *
     * @param request shipment 생성 요청 (송수신 주소, 소포 정보)
     * @return 생성된 shipment 응답 (rate 목록 포함)
     */
    public EasyPostShipmentResponse createShipment(EasyPostCreateShipmentRequest request) {
        try {
            Map<String, Object> body = toRequestMap(request);
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildAuthHeaders());
            EasyPostShipmentResponse response = restTemplate.exchange(
                    properties.getShipmentsUrl(),
                    HttpMethod.POST,
                    entity,
                    EasyPostShipmentResponse.class
            ).getBody();
            if (response == null) {
                throw new IllegalStateException("EasyPost createShipment returned empty response");
            }
            return response;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize EasyPost request", e);
        }
    }

    /**
     * 선택한 rate를 구매하고 라벨/추적 정보가 담긴 shipment 응답을 반환한다.
     *
     * @param shipmentId EasyPost shipment ID
     * @param rateId     구매할 rate ID
     * @return 구매 완료된 shipment 응답 (label URL, tracking URL 포함)
     */
    public EasyPostShipmentResponse buyRate(String shipmentId, String rateId) {
        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("rate", Map.of("id", rateId)));
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildAuthHeaders());
            EasyPostShipmentResponse response = restTemplate.exchange(
                    properties.getBuyRateUrl(shipmentId),
                    HttpMethod.POST,
                    entity,
                    EasyPostShipmentResponse.class
            ).getBody();
            if (response == null) {
                throw new IllegalStateException("EasyPost buyRate returned empty response");
            }
            return response;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize buyRate request", e);
        }
    }

    // 요청 DTO를 EasyPost가 기대하는 snake_case 맵 구조로 바꾼다.
    private Map<String, Object> toRequestMap(EasyPostCreateShipmentRequest request) {
        EasyPostCreateShipmentRequest.ShipmentBody sb = request.getShipment();
        if (sb == null) {
            throw new IllegalArgumentException("ShipmentBody must not be null");
        }
        Map<String, Object> shipment = new HashMap<>();
        if (sb.getToAddress() != null) {
            shipment.put("to_address", addressToMap(sb.getToAddress()));
        }
        if (sb.getFromAddress() != null) {
            shipment.put("from_address", addressToMap(sb.getFromAddress()));
        }
        if (sb.getParcel() != null) {
            shipment.put("parcel", parcelToMap(sb.getParcel()));
        }
        return Map.of("shipment", shipment);
    }

    // 주소 DTO에서 null이 아닌 필드만 골라 API 요청 body로 만든다.
    private Map<String, Object> addressToMap(EasyPostCreateShipmentRequest.AddressBody a) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "name", a.getName());
        putIfNotNull(map, "street1", a.getStreet1());
        putIfNotNull(map, "street2", a.getStreet2());
        putIfNotNull(map, "city", a.getCity());
        putIfNotNull(map, "state", a.getState());
        putIfNotNull(map, "zip", a.getZip());
        putIfNotNull(map, "country", a.getCountry());
        putIfNotNull(map, "phone", a.getPhone());
        putIfNotNull(map, "email", a.getEmail());
        return map;
    }

    // 소포 치수/무게도 null이 아닌 값만 전송한다.
    private Map<String, Object> parcelToMap(EasyPostCreateShipmentRequest.ParcelBody p) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "weight", p.getWeight());
        putIfNotNull(map, "length", p.getLength());
        putIfNotNull(map, "width", p.getWidth());
        putIfNotNull(map, "height", p.getHeight());
        return map;
    }

    // EasyPost는 누락 필드를 허용하므로 null 값은 요청에서 제거한다.
    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    // API key 기반 Basic Auth 헤더를 생성한다.
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
