package com.conk.integration.command.domain.repository;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 표준화된 채널 주문 aggregate를 저장/조회한다.
public interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {

    // 셀러별 주문 목록 조회에 사용된다.
    List<ChannelOrder> findBySellerId(String sellerId);
}
