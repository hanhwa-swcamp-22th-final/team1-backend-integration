package com.conk.integration.common.channel;

/**
 * 채널 코드를 화면 표시용 라벨로 변환하는 공통 유틸이다.
 */
public final class ChannelLabelResolver {

    private ChannelLabelResolver() {
    }

    /**
     * 알려진 채널 코드를 사람이 읽기 쉬운 라벨로 변환한다.
     *
     * @param channelName 채널 코드
     * @return 화면 표시에 사용할 채널 라벨
     */
    public static String toLabel(String channelName) {
        if (channelName == null) return "";
        return switch (channelName.toUpperCase()) {
            case "SHOPIFY" -> "Shopify";
            case "AMAZON"  -> "Amazon";
            case "MANUAL"  -> "Manual";
            case "EXCEL"   -> "Excel";
            default        -> channelName;
        };
    }
}
