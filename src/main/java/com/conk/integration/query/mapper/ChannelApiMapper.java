package com.conk.integration.query.mapper;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.common.channel.dto.ChannelConnectionInfo;
import com.conk.integration.common.channel.dto.ChannelCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

// 채널 API 자격증명 조회용 MyBatis 매퍼다.
@Mapper
public interface ChannelApiMapper {

    // sellerId와 channelName에 해당하는 채널 자격증명을 조회한다.
    ChannelCredential findChannelCredential(@Param("sellerId") String sellerId,
                                            @Param("channelName") String channelName);

    // 특정 셀러의 모든 채널 API 설정을 조회한다.
    List<ChannelApi> findByIdSellerId(@Param("sellerId") String sellerId);

    // 특정 셀러의 특정 채널 연결 상세를 조회한다.
    ChannelConnectionInfo findConnectionInfo(@Param("sellerId") String sellerId,
                                             @Param("channelName") String channelName);
}
