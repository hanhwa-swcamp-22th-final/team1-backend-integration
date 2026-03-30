package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.SellerChannelCardDto;
import com.conk.integration.command.domain.aggregate.*;
import com.conk.integration.command.domain.repository.ChannelApiRepository;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerChannelCardQueryService 단위 테스트")
class SellerChannelCardQueryServiceTest {

    @Mock private ChannelApiRepository channelApiRepository;
    @Mock private ChannelOrderRepository channelOrderRepository;

    @InjectMocks
    private SellerChannelCardQueryService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] 채널이 있고 주문이 있으면 카드 목록 반환")
    void getChannelCards_returnsCards_whenChannelsAndOrdersExist() {
        String sellerId = "seller-001";
        given(channelApiRepository.findByIdSellerId(sellerId))
                .willReturn(List.of(channelApi(sellerId, "SHOPIFY")));
        given(channelOrderRepository.findBySellerId(sellerId))
                .willReturn(List.of(
                        channelOrder("ORD-001", sellerId, OrderChannel.SHOPIFY, null, null, LocalDateTime.now()),
                        channelOrder("ORD-002", sellerId, OrderChannel.SHOPIFY, "inv-001", null, LocalDateTime.now().minusDays(1))
                ));

        List<SellerChannelCardDto> result = service.getChannelCards(sellerId);

        assertThat(result).hasSize(1);
        SellerChannelCardDto card = result.get(0);
        assertThat(card.getKey()).isEqualTo("SHOPIFY");
        assertThat(card.getLabel()).isEqualTo("Shopify");
        assertThat(card.getSyncStatus()).isEqualTo("ACTIVE");
        assertThat(card.getPendingOrders()).isEqualTo(1);   // invoiceNo null 1건
        assertThat(card.getTodayImported()).isEqualTo(1);   // 오늘 1건
        assertThat(card.getLastSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("[GREEN] 주문이 없으면 syncStatus=PLANNED, 통계 0")
    void getChannelCards_plannedStatus_whenNoOrders() {
        String sellerId = "seller-002";
        given(channelApiRepository.findByIdSellerId(sellerId))
                .willReturn(List.of(channelApi(sellerId, "AMAZON")));
        given(channelOrderRepository.findBySellerId(sellerId))
                .willReturn(List.of());

        List<SellerChannelCardDto> result = service.getChannelCards(sellerId);

        assertThat(result).hasSize(1);
        SellerChannelCardDto card = result.get(0);
        assertThat(card.getSyncStatus()).isEqualTo("PLANNED");
        assertThat(card.getPendingOrders()).isZero();
        assertThat(card.getTodayImported()).isZero();
        assertThat(card.getLastSyncedAt()).isNull();
    }

    @Test
    @DisplayName("[GREEN] 채널이 없으면 빈 리스트 반환")
    void getChannelCards_returnsEmpty_whenNoChannels() {
        String sellerId = "seller-003";
        given(channelApiRepository.findByIdSellerId(sellerId)).willReturn(List.of());
        given(channelOrderRepository.findBySellerId(sellerId)).willReturn(List.of());

        List<SellerChannelCardDto> result = service.getChannelCards(sellerId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[GREEN] 여러 채널이 있으면 각 채널별로 카드 반환")
    void getChannelCards_returnsCardPerChannel() {
        String sellerId = "seller-004";
        given(channelApiRepository.findByIdSellerId(sellerId))
                .willReturn(List.of(
                        channelApi(sellerId, "SHOPIFY"),
                        channelApi(sellerId, "AMAZON")
                ));
        given(channelOrderRepository.findBySellerId(sellerId))
                .willReturn(List.of(
                        channelOrder("ORD-010", sellerId, OrderChannel.SHOPIFY, null, null, LocalDateTime.now()),
                        channelOrder("ORD-011", sellerId, OrderChannel.AMAZON, null, null, LocalDateTime.now())
                ));

        List<SellerChannelCardDto> result = service.getChannelCards(sellerId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SellerChannelCardDto::getKey)
                .containsExactlyInAnyOrder("SHOPIFY", "AMAZON");
    }

    // ─────────────────────────────────────────────────────────
    // label 매핑
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] SHOPIFY → label 'Shopify'")
    void toLabel_shopify() {
        assertThat(service.toLabel("SHOPIFY")).isEqualTo("Shopify");
    }

    @Test
    @DisplayName("[GREEN] AMAZON → label 'Amazon'")
    void toLabel_amazon() {
        assertThat(service.toLabel("AMAZON")).isEqualTo("Amazon");
    }

    @Test
    @DisplayName("[GREEN] MANUAL → label 'Manual'")
    void toLabel_manual() {
        assertThat(service.toLabel("MANUAL")).isEqualTo("Manual");
    }

    @Test
    @DisplayName("[GREEN] EXCEL → label 'Excel'")
    void toLabel_excel() {
        assertThat(service.toLabel("EXCEL")).isEqualTo("Excel");
    }

    @Test
    @DisplayName("[GREEN] 알 수 없는 채널은 원본 값 그대로 반환")
    void toLabel_unknown_returnsAsIs() {
        assertThat(service.toLabel("TIKTOK")).isEqualTo("TIKTOK");
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] sellerId null → IllegalArgumentException")
    void getChannelCards_throwsWhenSellerIdNull() {
        assertThatThrownBy(() -> service.getChannelCards(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[예외] sellerId 빈 문자열 → IllegalArgumentException")
    void getChannelCards_throwsWhenSellerIdBlank() {
        assertThatThrownBy(() -> service.getChannelCards("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private ChannelApi channelApi(String sellerId, String channelName) {
        return ChannelApi.builder()
                .id(new ChannelApiId(sellerId, channelName))
                .channelApi("test-api-key")
                .build();
    }

    private ChannelOrder channelOrder(String orderId, String sellerId, OrderChannel channel,
                                      String invoiceNo, String shippedAt, LocalDateTime createdAt) {
        return ChannelOrder.builder()
                .orderId(orderId)
                .channelOrderNo("CH-" + orderId)
                .sellerId(sellerId)
                .orderChannel(channel)
                .invoiceNo(invoiceNo)
                .shippedAt(shippedAt)
                .orderedAt(createdAt)
                .createdAt(createdAt)
                .build();
    }
}
