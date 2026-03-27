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

@Service
@RequiredArgsConstructor
public class EasyPostApiClient {

    private final RestTemplate restTemplate;
    private final EasyPostProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EasyPostShipmentResponse createShipment(EasyPostCreateShipmentRequest request) {
        try {
            Map<String, Object> body = toRequestMap(request);
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildAuthHeaders());
            return restTemplate.exchange(
                    properties.getShipmentsUrl(),
                    HttpMethod.POST,
                    entity,
                    EasyPostShipmentResponse.class
            ).getBody();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize EasyPost request", e);
        }
    }

    public EasyPostShipmentResponse buyRate(String shipmentId, String rateId) {
        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("rate", Map.of("id", rateId)));
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, buildAuthHeaders());
            return restTemplate.exchange(
                    properties.getBuyRateUrl(shipmentId),
                    HttpMethod.POST,
                    entity,
                    EasyPostShipmentResponse.class
            ).getBody();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize buyRate request", e);
        }
    }

    private Map<String, Object> toRequestMap(EasyPostCreateShipmentRequest request) {
        EasyPostCreateShipmentRequest.ShipmentBody sb = request.getShipment();
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

    private Map<String, Object> parcelToMap(EasyPostCreateShipmentRequest.ParcelBody p) {
        Map<String, Object> map = new HashMap<>();
        putIfNotNull(map, "weight", p.getWeight());
        putIfNotNull(map, "length", p.getLength());
        putIfNotNull(map, "width", p.getWidth());
        putIfNotNull(map, "height", p.getHeight());
        return map;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
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
