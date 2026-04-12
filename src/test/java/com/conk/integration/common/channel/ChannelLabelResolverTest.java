package com.conk.integration.common.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelLabelResolver 테스트")
class ChannelLabelResolverTest {

    @Test
    @DisplayName("SHOPIFY가 주어지면 표시 이름으로 변환했을 때 Shopify를 반환해야 한다")
    void toLabel_shopify() {
        assertThat(ChannelLabelResolver.toLabel("SHOPIFY")).isEqualTo("Shopify");
    }

    @Test
    @DisplayName("AMAZON이 주어지면 표시 이름으로 변환했을 때 Amazon을 반환해야 한다")
    void toLabel_amazon() {
        assertThat(ChannelLabelResolver.toLabel("AMAZON")).isEqualTo("Amazon");
    }

    @Test
    @DisplayName("MANUAL이 주어지면 표시 이름으로 변환했을 때 Manual을 반환해야 한다")
    void toLabel_manual() {
        assertThat(ChannelLabelResolver.toLabel("MANUAL")).isEqualTo("Manual");
    }

    @Test
    @DisplayName("EXCEL이 주어지면 표시 이름으로 변환했을 때 Excel을 반환해야 한다")
    void toLabel_excel() {
        assertThat(ChannelLabelResolver.toLabel("EXCEL")).isEqualTo("Excel");
    }

    @Test
    @DisplayName("알 수 없는 채널명이 주어지면 표시 이름으로 변환했을 때 원본 값을 반환해야 한다")
    void toLabel_unknown_returnsAsIs() {
        assertThat(ChannelLabelResolver.toLabel("UNKNOWN_CHANNEL")).isEqualTo("UNKNOWN_CHANNEL");
    }

    @Test
    @DisplayName("채널명이 null이면 표시 이름으로 변환했을 때 빈 문자열을 반환해야 한다")
    void toLabel_null_returnsEmpty() {
        assertThat(ChannelLabelResolver.toLabel(null)).isEqualTo("");
    }
}
