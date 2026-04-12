package com.conk.integration.common.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 셀러 채널 연결 상세 응답 DTO다.
 */
@Getter
@AllArgsConstructor
public class SellerChannelDetailDto {

    private String channelName;
    private String storeName;
    private String channelApi;
    private LocalDateTime connectedAt;
}
