package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// 채널별 syncer 선택과 주문 동기화 실행을 담당하는 orchestration 서비스다.
@Service
@RequiredArgsConstructor
public class ChannelOrderSyncDispatchService {

    private final List<ChannelOrderSyncer> syncers;

    /**
     * 요청 채널에 맞는 syncer를 선택해 주문 동기화를 실행한다.
     *
     * @param sellerId     동기화할 셀러 식별자
     * @param orderChannel 대상 채널
     * @return 저장/skip 건수 및 저장된 주문 목록
     * @throws IllegalArgumentException 지원하는 syncer가 없는 경우
     */
    public ChannelOrderSyncResponse sync(String sellerId, OrderChannel orderChannel) {
        ChannelOrderSyncer syncer = syncers.stream()
                .filter(candidate -> candidate.supports(orderChannel))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 주문 동기화 채널입니다: " + orderChannel));

        return syncer.syncOrders(sellerId);
    }
}
