package com.conk.integration.command.infrastructure.mapper;

import com.conk.integration.command.application.dto.FulfillmentTargetDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// fulfillment 전송 대상 조회 및 동기화 상태 업데이트 MyBatis 매퍼다.
@Mapper
public interface ChannelFulfillmentMapper {

    List<FulfillmentTargetDto> findUnsyncedTargets(
            @Param("sellerId") String sellerId,
            @Param("orderChannel") String orderChannel);

    // channel_sync_yn을 true로 일괄 업데이트한다.
    void markAllSynced(@Param("orderIds") List<String> orderIds);
}
