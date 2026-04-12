package com.conk.integration.common.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 채널 연결 상세 조회에 필요한 최소 필드만 담는 read model이다.
 */
@Getter
@AllArgsConstructor
public class ChannelConnectionInfo {

    private String channelName;
    private String storeName;
    private String channelApi;
    private LocalDateTime connectedAt;
}
