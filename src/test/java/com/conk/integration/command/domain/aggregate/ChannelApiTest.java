package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.repository.ChannelApiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("ChannelApi Entity Tests")
class ChannelApiTest {

    @Autowired
    private ChannelApiRepository repository;

    @Test
    @DisplayName("채널 API 복합키 저장")
    void save_ChannelApi_WithCompositeKey() {
        // given
        ChannelApiId id = new ChannelApiId("seller-001", "AMAZON");
        ChannelApi api = ChannelApi.builder()
                .id(id)
                .channelApi("api-key-xyz-amazon")
                .build();

        // when
        ChannelApi saved = repository.save(api);

        // then
        assertThat(saved.getId().getSellerId()).isEqualTo("seller-001");
        assertThat(saved.getId().getChannelName()).isEqualTo("AMAZON");
        assertThat(saved.getChannelApi()).isEqualTo("api-key-xyz-amazon");
    }

    @Test
    @DisplayName("복합키로 채널 API 조회")
    void findById_WithCompositeKey() {
        // given
        ChannelApiId id = new ChannelApiId("seller-002", "SHOPIFY");
        ChannelApi api = ChannelApi.builder()
                .id(id)
                .channelApi("api-key-shopify-001")
                .build();
        repository.save(api);

        // when
        Optional<ChannelApi> found = repository.findById(id);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getChannelApi()).isEqualTo("api-key-shopify-001");
    }

    @Test
    @DisplayName("동일 sellerId, 다른 channelName 복수 저장")
    void save_SameSeller_DifferentChannel() {
        // given
        ChannelApi amazon = ChannelApi.builder()
                .id(new ChannelApiId("seller-003", "AMAZON"))
                .channelApi("amazon-key")
                .build();
        ChannelApi shopify = ChannelApi.builder()
                .id(new ChannelApiId("seller-003", "SHOPIFY"))
                .channelApi("shopify-key")
                .build();

        // when
        repository.save(amazon);
        repository.save(shopify);

        // then
        List<ChannelApi> all = repository.findAll();
        long count = all.stream()
                .filter(a -> "seller-003".equals(a.getId().getSellerId()))
                .count();
        assertThat(count).isEqualTo(2);
    }
}
