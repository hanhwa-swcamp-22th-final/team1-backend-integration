package com.conk.integration.common.channel;

import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;

import java.util.Locale;

/**
 * channelKey 문자열을 정규화하고 지원 채널 여부를 판별하는 공통 유틸이다.
 */
public final class ChannelKeyResolver {

    private ChannelKeyResolver() {
    }

    /**
     * channelKey를 trim + upper-case로 정규화하고 공백 여부를 검증한다.
     *
     * @param channelKey 정규화할 채널 문자열
     * @param errorCode 검증 실패 시 사용할 에러 코드
     * @return 정규화된 채널 문자열
     */
    public static String normalize(String channelKey, ErrorCode errorCode) {
        return normalize(channelKey, errorCode, null);
    }

    /**
     * channelKey를 trim + upper-case로 정규화하고 공백 여부를 검증한다.
     *
     * @param channelKey 정규화할 채널 문자열
     * @param errorCode 검증 실패 시 사용할 에러 코드
     * @param errorMessage 검증 실패 시 사용할 상세 메시지
     * @return 정규화된 채널 문자열
     * @throws BusinessException channelKey가 비어 있는 경우
     */
    public static String normalize(String channelKey, ErrorCode errorCode, String errorMessage) {
        String normalized = channelKey == null ? "" : channelKey.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw errorMessage == null
                    ? new BusinessException(errorCode)
                    : new BusinessException(errorCode, errorMessage);
        }
        return normalized;
    }

    /**
     * 문자열 channelKey가 기대한 지원 채널인지 확인한다.
     *
     * @param channelKey 검증할 정규화 채널 문자열
     * @param supportedChannelKey 지원하는 채널 문자열
     * @param errorMessagePrefix 예외 메시지 접두어
     * @throws BusinessException 지원하지 않는 채널인 경우 (INT-004)
     */
    public static void requireSupported(String channelKey, String supportedChannelKey, String errorMessagePrefix) {
        if (!supportedChannelKey.equals(channelKey)) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_CHANNEL,
                    errorMessagePrefix + channelKey);
        }
    }

    /**
     * OrderChannel 값이 기대한 지원 채널인지 확인한다.
     *
     * @param orderChannel 검증할 주문 채널 enum
     * @param supportedChannel 지원하는 주문 채널 enum
     * @param errorMessagePrefix 예외 메시지 접두어
     * @throws BusinessException 지원하지 않는 채널인 경우 (INT-004)
     */
    public static void requireSupported(OrderChannel orderChannel, OrderChannel supportedChannel, String errorMessagePrefix) {
        if (orderChannel != supportedChannel) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_CHANNEL,
                    errorMessagePrefix + orderChannel);
        }
    }

    /**
     * channelKey를 OrderChannel enum으로 변환한다.
     *
     * @param channelKey 변환할 채널 문자열
     * @param errorMessagePrefix 변환 실패 시 사용할 예외 메시지 접두어
     * @return 변환된 OrderChannel 값
     * @throws BusinessException 지원하지 않는 채널인 경우 (INT-004)
     */
    public static OrderChannel toOrderChannel(String channelKey, String errorMessagePrefix) {
        String normalized = normalize(
                channelKey,
                ErrorCode.UNSUPPORTED_CHANNEL,
                errorMessagePrefix + channelKey);
        try {
            return OrderChannel.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_CHANNEL,
                    errorMessagePrefix + channelKey);
        }
    }
}
