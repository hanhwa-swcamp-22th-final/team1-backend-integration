package com.conk.integration.command.domain.repository;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// 표준화된 채널 주문 aggregate를 저장/조회한다.
public interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {

    // 셀러별 주문 목록 조회에 사용된다.
    List<ChannelOrder> findBySellerId(String sellerId);

    // 일괄 fulfillment 전송 완료 후 채널 동기화 상태를 true로 일괄 업데이트한다.
    @Modifying
    @Query("UPDATE ChannelOrder co SET co.channelSyncYn = true WHERE co.orderId IN :orderIds")
    void markAllSynced(@Param("orderIds") List<String> orderIds);

    // 송장 발급 후 주문에 invoiceNo를 반영한다. clearAutomatically로 1차 캐시를 초기화해 이후 조회가 DB에서 읽히도록 한다.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChannelOrder co SET co.invoiceNo = :invoiceNo WHERE co.orderId = :orderId")
    void updateInvoiceNo(@Param("orderId") String orderId, @Param("invoiceNo") String invoiceNo);
}
