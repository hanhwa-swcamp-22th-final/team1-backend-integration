package com.conk.integration.command.infrastructure.mapper;

import com.conk.integration.command.application.dto.InvoiceTargetDto;
import com.conk.integration.command.application.dto.request.OrderInvoicePair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 송장 발급 대상 주문 조회 및 송장 번호 일괄 반영 MyBatis 매퍼다.
@Mapper
public interface ChannelOrderInvoiceMapper {

    List<InvoiceTargetDto> findOrdersWithoutInvoice(@Param("sellerId") String sellerId);

    // 주문별로 다른 invoiceNo를 한 번의 쿼리로 일괄 반영한다.
    void bulkAssignInvoice(@Param("pairs") List<OrderInvoicePair> pairs);
}
