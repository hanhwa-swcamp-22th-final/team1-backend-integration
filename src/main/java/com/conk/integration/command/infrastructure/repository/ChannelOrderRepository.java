package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 표준화된 채널 주문 aggregate를 저장/조회한다.
public interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {

    // 셀러별 주문 목록 조회에 사용된다.
    List<ChannelOrder> findBySellerId(String sellerId);

    // 셀러/채널 기준 가장 최근에 저장된 주문 1건을 조회한다.
    Optional<ChannelOrder> findFirstBySellerIdAndOrderChannelOrderByAuditCreatedAtDesc(
            String sellerId,
            OrderChannel orderChannel);

    // 동일 셀러+채널 주문번호가 이미 저장되어 있는지 확인한다 (sync 멱등성 보장).
    boolean existsBySellerIdAndChannelOrderNo(String sellerId, String channelOrderNo);
}
