package com.conk.integration.common.channel;

import com.conk.integration.command.domain.aggregate.enums.OrderChannel;

/**
 * 채널별 구현체가 공통으로 제공해야 하는 지원 채널 판별 계약이다.
 */
public interface ChannelStrategy {

    /**
     * 주어진 채널을 현재 구현체가 처리할 수 있는지 확인한다.
     *
     * @param channel 확인할 주문 채널
     * @return 지원하면 true
     */
    boolean supports(OrderChannel channel);
}
