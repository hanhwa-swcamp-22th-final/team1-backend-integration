package com.conk.integration.query.mapper;

import com.conk.integration.query.dto.InvoiceTargetDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 일괄 송장 발급 대상 주문 조회용 MyBatis 매퍼다.
@Mapper
public interface ChannelOrderInvoiceMapper {

    // invoiceNo가 없는(미발급) 주문을 셀러별로 조회한다.
    List<InvoiceTargetDto> findOrdersWithoutInvoice(@Param("sellerId") String sellerId);
}