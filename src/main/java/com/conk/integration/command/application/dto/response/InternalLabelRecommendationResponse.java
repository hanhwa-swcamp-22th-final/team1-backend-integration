package com.conk.integration.command.application.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * WMS 내부 추천 배송 응답 DTO다.
 */
@Getter
@Builder
public class InternalLabelRecommendationResponse {

    private String recommendedCarrier;
    private String recommendedService;
    private double estimatedRate;
    private double weightLbs;
}
