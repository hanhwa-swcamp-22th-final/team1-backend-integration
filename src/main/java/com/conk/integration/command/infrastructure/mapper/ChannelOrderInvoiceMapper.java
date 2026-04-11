package com.conk.integration.command.infrastructure.mapper;

import com.conk.integration.command.application.dto.InvoiceTargetDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 일괄 송장 발급 대상 주문 조회용 MyBatis 매퍼다.
@Mapper
public interface ChannelOrderInvoiceMapper {

    List<InvoiceTargetDto> findOrdersWithoutInvoice(@Param("sellerId") String sellerId);
}
