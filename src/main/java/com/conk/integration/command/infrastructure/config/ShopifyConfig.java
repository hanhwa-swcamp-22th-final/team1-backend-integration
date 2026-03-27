package com.conk.integration.command.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(ShopifyProperties.class)
public class ShopifyConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
