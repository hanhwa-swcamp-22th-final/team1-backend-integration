package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;

// 채널별 주문 동기화 전략이 따라야 하는 최소 계약이다.
public interface ChannelOrderSyncer {

    /**
     * 이 syncer가 주어진 채널을 지원하는지 확인한다.
     *
     * @param channel 확인할 주문 채널
     * @return 지원하면 true
     */
    boolean supports(OrderChannel channel);

    /**
     * 채널에서 주문을 가져와 channel_order + channel_order_item에 저장한다.
     *
     * @param sellerId 동기화할 셀러 식별자
     * @return 저장/skip 건수 및 저장된 주문 목록
     */
    ChannelOrderSyncResponse syncOrders(String sellerId);
}
