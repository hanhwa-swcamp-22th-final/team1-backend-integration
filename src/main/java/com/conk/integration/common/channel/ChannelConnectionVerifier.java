package com.conk.integration.common.channel;

/**
 * 채널별 연결 검증 구현체가 따라야 하는 계약이다.
 */
public interface ChannelConnectionVerifier {

    /**
     * 주어진 채널 키를 현재 구현체가 처리할 수 있는지 확인한다.
     *
     * @param channelKey 정규화된 채널 키
     * @return 지원하면 true
     */
    boolean supports(String channelKey);

    /**
     * 주어진 연결 정보로 채널 연결 가능 여부를 검증한다.
     *
     * @param storeName 채널 스토어/상점 식별자
     * @param channelApi 채널 API 토큰 또는 비밀값
     * @return 연결 가능하면 true
     */
    boolean verify(String storeName, String channelApi);

    /**
     * 검증 실패 시 표시할 기본 메시지다.
     *
     * @param channelKey 정규화된 채널 키
     * @return 검증 실패 메시지
     */
    default String failureMessage(String channelKey) {
        return channelKey + " 채널 연결에 실패했습니다.";
    }
}
