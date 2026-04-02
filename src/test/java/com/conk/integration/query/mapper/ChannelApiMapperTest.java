package com.conk.integration.query.mapper;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import com.conk.integration.command.infrastructure.repository.ChannelApiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ChannelApiMapper는 sellerId 기반 채널 API 조회 동작만 좁게 검증한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("ChannelApiMapper Tests")
class ChannelApiMapperTest {

    @Autowired
    private ChannelApiMapper channelApiMapper;

    @Autowired
    private ChannelApiRepository channelApiRepository;

    // 한 셀러에 여러 채널이 연결된 상황에서 sellerId 필터가 정확히 동작해야 한다.
    @Test
    @DisplayName("findByIdSellerId()는 해당 sellerId의 채널 API만 반환한다")
    void findByIdSellerId_returnsMatchingChannelApis() {
        channelApiRepository.saveAllAndFlush(List.of(
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

        List<ChannelApi> result = channelApiMapper.findByIdSellerId("seller-A");

        assertThat(result).hasSize(2)
                .extracting(api -> api.getId().getSellerId())
                .containsOnly("seller-A");
    }

    // 연결 정보가 없는 셀러는 빈 결과로 처리되어야 조회 API가 단순해진다.
    @Test
    @DisplayName("findByIdSellerId()는 일치하는 sellerId가 없으면 빈 리스트를 반환한다")
    void findByIdSellerId_returnsEmptyWhenNoMatch() {
        List<ChannelApi> result = channelApiMapper.findByIdSellerId("seller-NONE");

        assertThat(result).isEmpty();
    }
}