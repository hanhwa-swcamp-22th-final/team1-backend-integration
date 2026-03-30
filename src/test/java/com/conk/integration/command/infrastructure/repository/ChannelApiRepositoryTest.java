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

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ChannelApiRepository Tests")
class ChannelApiRepositoryTest {

    @Autowired
    private ChannelApiRepository channelApiRepository;

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

    @Test
    @DisplayName("findByIdSellerId()는 일치하는 sellerId가 없으면 빈 리스트를 반환한다")
    void findByIdSellerId_returnsEmptyWhenNoMatch() {
        List<ChannelApi> result = channelApiRepository.findByIdSellerId("seller-NONE");

        assertThat(result).isEmpty();
    }

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
