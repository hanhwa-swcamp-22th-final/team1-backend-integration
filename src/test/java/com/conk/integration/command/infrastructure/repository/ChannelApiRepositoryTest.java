package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.ChannelApiId;
import com.conk.integration.command.domain.repository.ChannelApiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ChannelApiRepository는 복합 키와 sellerId 기반 조회 동작만 좁게 검증한다.
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ChannelApiRepository Tests")
class ChannelApiRepositoryTest {

    @Autowired
    private ChannelApiRepository channelApiRepository;

    // 한 셀러에 여러 채널이 연결된 상황에서 sellerId 필터가 정확히 동작해야 한다.
    @Test
    @DisplayName("findByIdSellerId()는 해당 sellerId의 채널 API만 반환한다")
    void findByIdSellerId_returnsMatchingChannelApis() {
        channelApiRepository.saveAll(List.of(
                ChannelApi.builder()
                        .id(new ChannelApiId("seller-A", "SHOPIFY"))
                        .channelApi("shopify-token-A")
                        .build(),
                ChannelApi.builder()
                        .id(new ChannelApiId("seller-A", "AMAZON"))
                        .channelApi("amazon-token-A")
                        .build(),
                ChannelApi.builder()
                        .id(new ChannelApiId("seller-B", "SHOPIFY"))
                        .channelApi("shopify-token-B")
                        .build()
        ));

        List<ChannelApi> result = channelApiRepository.findByIdSellerId("seller-A");

        assertThat(result).hasSize(2)
                .extracting(api -> api.getId().getSellerId())
                .containsOnly("seller-A");
    }

    // 연결 정보가 없는 셀러는 빈 결과로 처리되어야 조회 API가 단순해진다.
    @Test
    @DisplayName("findByIdSellerId()는 일치하는 sellerId가 없으면 빈 리스트를 반환한다")
    void findByIdSellerId_returnsEmptyWhenNoMatch() {
        List<ChannelApi> result = channelApiRepository.findByIdSellerId("seller-NONE");

        assertThat(result).isEmpty();
    }

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
