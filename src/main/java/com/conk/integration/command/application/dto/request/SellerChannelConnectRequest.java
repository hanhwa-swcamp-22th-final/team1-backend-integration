package com.conk.integration.command.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 셀러 채널 연결 요청 body를 표현한다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SellerChannelConnectRequest {

    private String storeName;
    private String channelApi;
    private String storeAlias;
    private String contactEmail;
    private String syncMode;
}
