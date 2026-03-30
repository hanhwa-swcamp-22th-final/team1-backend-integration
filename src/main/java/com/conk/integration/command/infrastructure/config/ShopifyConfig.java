package com.conk.integration.command.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

// Shopify API 호출에 필요한 공통 빈을 등록한다.
@Configuration
@EnableConfigurationProperties(ShopifyProperties.class)
public class ShopifyConfig {

    // 현재 프로젝트는 동기식 외부 API 호출에 RestTemplate을 사용한다.
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
