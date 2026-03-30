package com.conk.integration.command.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// EasyPost 설정 프로퍼티 바인딩을 활성화한다.
@Configuration
@EnableConfigurationProperties(EasyPostProperties.class)
public class EasyPostConfig {
}
