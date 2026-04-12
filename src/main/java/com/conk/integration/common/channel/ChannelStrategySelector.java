package com.conk.integration.common.channel;

import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;

import java.util.List;

/**
 * 채널별 전략 목록에서 요청 채널에 맞는 구현체를 선택한다.
 */
public final class ChannelStrategySelector {

    private ChannelStrategySelector() {
    }

    /**
     * 전략 목록에서 요청 채널을 지원하는 첫 구현체를 반환한다.
     *
     * @param strategies 선택 대상 전략 목록
     * @param orderChannel 요청 채널
     * @param errorMessagePrefix 선택 실패 시 사용할 예외 메시지 접두어
     * @param <T> 채널 전략 타입
     * @return 요청 채널을 지원하는 전략 구현체
     * @throws BusinessException 지원하는 구현체가 없는 경우 (INT-004)
     */
    public static <T extends ChannelStrategy> T select(
            List<T> strategies,
            OrderChannel orderChannel,
            String errorMessagePrefix) {
        return strategies.stream()
                .filter(s -> s.supports(orderChannel))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNSUPPORTED_CHANNEL,
                        errorMessagePrefix + orderChannel));
    }
}
