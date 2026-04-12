package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.channel.ChannelStrategy;

/**
 * 채널별 주문 import 미리보기 전략 계약이다.
 */
public interface ChannelImportPreviewProvider extends ChannelStrategy {

    /**
     * preview 대상 채널을 지원하는지 확인한다.
     *
     * @param channel 확인할 채널
     * @return 지원하면 true
     */
    @Override
    boolean supports(OrderChannel channel);

    /**
     * 채널별 import preview를 계산한다.
     *
     * @param sellerId 셀러 식별자
     * @param request 미리보기 요청 본문
     * @return preview 응답 DTO
     */
    SellerChannelImportPreviewResponse preview(String sellerId, SellerChannelImportPreviewRequest request);
}
