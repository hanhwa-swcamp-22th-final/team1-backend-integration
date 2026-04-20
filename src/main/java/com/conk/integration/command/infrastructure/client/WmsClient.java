package com.conk.integration.command.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

// WMS 서비스 내부 호출 클라이언트 — SKU 등록 여부 확인에 사용한다.
@Slf4j
@Component
public class WmsClient {

    private final RestTemplate restTemplate;
    private final String skuExistsUrlTemplate;

    public WmsClient(
            RestTemplate restTemplate,
            @Value("${wms.service.url}") String wmsServiceUrl) {
        this.restTemplate = restTemplate;
        this.skuExistsUrlTemplate = wmsServiceUrl + "/wms/internal/skus/{sku}/exists";
        log.info("WmsClient 초기화: skuExistsUrlTemplate={}", this.skuExistsUrlTemplate);
    }

    /**
     * 해당 SKU가 WMS에 등록되어 있는지 확인한다.
     * WMS 호출 실패 시 true를 반환해 주문 처리가 중단되지 않도록 한다.
     */
    public boolean skuExists(String sku) {
        try {
            Boolean result = restTemplate.getForObject(skuExistsUrlTemplate, Boolean.class, sku);
            return Boolean.TRUE.equals(result);
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("WMS SKU 존재 확인 실패 (sku={}, error={}) — 처리 계속 진행", sku, e.getMessage());
            return true;
        }
    }
}
