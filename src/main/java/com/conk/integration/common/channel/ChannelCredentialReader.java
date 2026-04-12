package com.conk.integration.common.channel;

import com.conk.integration.common.channel.dto.ChannelCredential;

/**
 * 채널 자격증명 조회 구현체가 따라야 하는 계약이다.
 */
public interface ChannelCredentialReader {

    /**
     * 주어진 채널 키를 현재 구현체가 처리할 수 있는지 확인한다.
     *
     * @param channelKey 정규화된 채널 키
     * @return 지원하면 true
     */
    boolean supports(String channelKey);

    /**
     * sellerId와 channelKey 기준으로 채널 자격증명을 읽는다.
     *
     * @param sellerId 셀러 식별자
     * @param channelKey 정규화된 채널 키
     * @return 채널 자격증명
     */
    ChannelCredential read(String sellerId, String channelKey);
}
