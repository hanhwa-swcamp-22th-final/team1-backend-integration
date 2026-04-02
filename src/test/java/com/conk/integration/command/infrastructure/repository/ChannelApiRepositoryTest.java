package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import com.conk.integration.command.infrastructure.repository.ChannelApiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// ChannelApiRepository는 복합 키 기반 JPA 동작만 좁게 검증한다.
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ChannelApiRepository Tests")
class ChannelApiRepositoryTest {

    @Autowired
    private ChannelApiRepository channelApiRepository;

    // 복합 키 조회는 개별 채널 설정을 수정/조회하는 기본 동작이다.
    @Test
    @DisplayName("복합 키로 findById() 조회 시 올바른 엔티티가 반환된다")
    void findById_withCompositeKey_returnsCorrectEntity() {
        ChannelApiId id = new ChannelApiId("seller-C", "SHOPIFY");
        channelApiRepository.save(
                ChannelApi.builder()
                        .id(id)
                        .channelApi("shopify-api-key")
                        .build()
        );

        ChannelApi found = channelApiRepository.findById(id).orElseThrow();

        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getChannelApi()).isEqualTo("shopify-api-key");
    }
}