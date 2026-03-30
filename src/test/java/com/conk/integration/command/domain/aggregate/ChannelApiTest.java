package com.conk.integration.command.domain.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelApi Entity Tests")
class ChannelApiTest {

    @Test
    @DisplayName("빌더로 생성하면 복합 키와 API 토큰이 그대로 설정된다")
    void builder_setsCompositeKeyAndApiToken() {
        ChannelApi api = ChannelApi.builder()
                .id(new ChannelApiId("seller-001", "AMAZON"))
                .channelApi("api-key-xyz-amazon")
                .createdBy("tester")
                .build();

        assertThat(api.getId().getSellerId()).isEqualTo("seller-001");
        assertThat(api.getId().getChannelName()).isEqualTo("AMAZON");
        assertThat(api.getChannelApi()).isEqualTo("api-key-xyz-amazon");
        assertThat(api.getCreatedBy()).isEqualTo("tester");
    }

    @Test
    @DisplayName("생성 시 감사 필드를 지정하면 값이 유지된다")
    void builder_keepsAuditFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 30, 9, 0);
        LocalDateTime updatedAt = createdAt.plusHours(1);

        ChannelApi api = ChannelApi.builder()
                .id(new ChannelApiId("seller-002", "SHOPIFY"))
                .channelApi("api-key-shopify-001")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .updatedBy("auditor")
                .build();

        assertThat(api.getCreatedAt()).isEqualTo(createdAt);
        assertThat(api.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(api.getUpdatedBy()).isEqualTo("auditor");
    }
}
