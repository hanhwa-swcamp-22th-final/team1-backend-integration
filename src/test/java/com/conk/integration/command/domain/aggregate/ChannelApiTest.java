package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 순수 객체 생성 관점에서 ChannelApi의 값 보존만 검증한다.
@DisplayName("ChannelApi Entity Tests")
class ChannelApiTest {

    // 복합 키와 토큰이 빌더 입력 그대로 유지되는지 본다.
    @Test
    @DisplayName("빌더로 생성하면 복합 키와 API 토큰이 그대로 설정된다")
    void builder_setsCompositeKeyAndApiToken() {
        ChannelApi api = ChannelApi.builder()
                .id(new ChannelApiId("seller-001", "AMAZON"))
                .channelApi("api-key-xyz-amazon")
                .audit(AuditFields.builder().createdBy("tester").build())
                .build();

        assertThat(api.getId().getSellerId()).isEqualTo("seller-001");
        assertThat(api.getId().getChannelName()).isEqualTo("AMAZON");
        assertThat(api.getChannelApi()).isEqualTo("api-key-xyz-amazon");
        assertThat(api.getAudit().getCreatedBy()).isEqualTo("tester");
    }

    // 감사 필드는 영속화 없이도 단순 값 보존이 가능해야 한다.
    @Test
    @DisplayName("생성 시 감사 필드를 지정하면 값이 유지된다")
    void builder_keepsAuditFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 30, 9, 0);
        LocalDateTime updatedAt = createdAt.plusHours(1);

        ChannelApi api = ChannelApi.builder()
                .id(new ChannelApiId("seller-002", "SHOPIFY"))
                .channelApi("api-key-shopify-001")
                .audit(AuditFields.builder()
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .updatedBy("auditor")
                        .build())
                .build();

        assertThat(api.getAudit().getCreatedAt()).isEqualTo(createdAt);
        assertThat(api.getAudit().getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(api.getAudit().getUpdatedBy()).isEqualTo("auditor");
    }
}
