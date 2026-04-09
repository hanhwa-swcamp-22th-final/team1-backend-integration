package com.conk.integration.query.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

// 셀러 채널 상세 연결 정보 응답 DTO
@Getter
@AllArgsConstructor
public class SellerChannelDetailDto {

    private String channelName;
    private String storeName;
    private String channelApi;
    private LocalDateTime connectedAt;
}
