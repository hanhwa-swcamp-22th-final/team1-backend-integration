package com.conk.integration.command.infrastructure.mapper;

import com.conk.integration.command.application.dto.FulfillmentTargetDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 일괄 fulfillment 전송 대상 조회용 MyBatis 매퍼다.
@Mapper
public interface ChannelFulfillmentMapper {

    List<FulfillmentTargetDto> findUnsyncedTargets(
            @Param("sellerId") String sellerId,
            @Param("orderChannel") String orderChannel);
}
