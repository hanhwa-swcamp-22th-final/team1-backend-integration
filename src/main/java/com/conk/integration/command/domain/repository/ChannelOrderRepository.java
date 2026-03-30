package com.conk.integration.command.domain.repository;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {

    List<ChannelOrder> findBySellerId(String sellerId);

    /** 셀러의 모든 주문을 items 함께 조회 (N+1 방지) */
    @Query("SELECT DISTINCT co FROM ChannelOrder co LEFT JOIN FETCH co.items WHERE co.sellerId = :sellerId")
    List<ChannelOrder> findBySellerIdWithItems(@Param("sellerId") String sellerId);
}
