package com.conk.integration.query.mapper;

import com.conk.integration.query.dto.FulfillmentTargetDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 일괄 fulfillment 전송 대상 조회용 MyBatis 매퍼다.
@Mapper
public interface ChannelFulfillmentMapper {

    // channelSyncYn=false이고 invoiceNo가 있는 미전송 주문을 채널별로 조회한다.
    List<FulfillmentTargetDto> findUnsyncedTargets(
            @Param("sellerId") String sellerId,
            @Param("orderChannel") String orderChannel);
}