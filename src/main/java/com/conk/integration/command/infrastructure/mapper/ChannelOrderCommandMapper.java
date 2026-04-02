package com.conk.integration.command.infrastructure.mapper;

import com.conk.integration.command.application.dto.request.OrderInvoicePair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChannelOrderCommandMapper {

    // channel_sync_yn을 true로 일괄 업데이트한다.
    void markAllSynced(@Param("orderIds") List<String> orderIds);

    // 주문별로 다른 invoiceNo를 한 번의 쿼리로 일괄 반영한다.
    void bulkAssignInvoice(@Param("pairs") List<OrderInvoicePair> pairs);
}
