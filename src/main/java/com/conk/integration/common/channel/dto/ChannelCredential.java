package com.conk.integration.common.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 채널 자격증명 조회에 필요한 최소 필드를 담는 공통 DTO다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCredential {

    private String channelName;
    private String storeName;
    private String channelApi;

    public String getAccessToken() {
        return channelApi;
    }

    public void setAccessToken(String accessToken) {
        this.channelApi = accessToken;
    }
}
