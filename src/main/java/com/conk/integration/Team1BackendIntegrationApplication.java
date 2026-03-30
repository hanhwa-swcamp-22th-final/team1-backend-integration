package com.conk.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 통합 모듈의 스프링 부트 진입점이다.
@SpringBootApplication
public class Team1BackendIntegrationApplication {

  // 표준 Spring Boot 부트스트랩.
  public static void main(String[] args) {
    SpringApplication.run(Team1BackendIntegrationApplication.class, args);
  }

}
