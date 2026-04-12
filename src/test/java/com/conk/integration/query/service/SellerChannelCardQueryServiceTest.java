package com.conk.integration.query.service;

import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.common.channel.dto.ShopifyCredentialDto;
import com.conk.integration.query.mapper.SellerChannelCardMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 채널 카드 조회 서비스의 입력 검증, 라벨 후처리, Shopify ping 연동 상태를 검증한다.
@ExtendWith(MockitoExtension.class)
@DisplayName("SellerChannelCardQueryService 테스트")
class SellerChannelCardQueryServiceTest {

    @Mock private SellerChannelCardMapper channelCardMapper;
    @Mock private ChannelApiQueryService channelApiQueryService;
    @Mock private ShopifyPingClient shopifyPingClient;
    @InjectMocks private SellerChannelCardQueryService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("채널 카드 조회 결과가 주어지면 채널 카드 목록을 조회했을 때 label을 채워 반환해야 한다")
    void getChannelCards_delegatesToMapper_andFillsLabel() {
        // mapper raw 값은 유지하고 label만 후처리되는지 확인한다.
        SellerChannelCardDto dto = buildCard("SHOPIFY", "ACTIVE", 3, 1, LocalDateTime.now());
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1")).willReturn(List.of(dto));
        given(channelApiQueryService.findShopifyCredential("seller-1")).willReturn(buildCredential());
        given(shopifyPingClient.ping(any(), any())).willReturn(true);

        List<SellerChannelCardDto> result = service.getChannelCards("seller-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("Shopify");
        verify(channelCardMapper).findBySellerIdGroupedByChannel("seller-1");
    }

    @Test
    @DisplayName("채널 카드 조회 결과가 비어 있으면 채널 카드 목록을 조회했을 때 빈 리스트를 반환해야 한다")
    void getChannelCards_returnsEmpty_whenMapperReturnsEmpty() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1")).willReturn(List.of());

        List<SellerChannelCardDto> result = service.getChannelCards("seller-1");

        assertThat(result).isEmpty();
        verify(channelCardMapper).findBySellerIdGroupedByChannel("seller-1");
    }

    @Test
    @DisplayName("Shopify가 아닌 채널의 ACTIVE 상태가 주어지면 채널 카드 목록을 조회했을 때 syncStatus를 그대로 유지해야 한다")
    void getChannelCards_activeSyncStatus_preserved() {
        // SHOPIFY 이외 채널은 ping 없이 DB 값을 그대로 반환해야 한다.
        SellerChannelCardDto dto = buildCard("AMAZON", "ACTIVE", 0, 0, null);
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1")).willReturn(List.of(dto));

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("PLANNED 상태가 주어지면 채널 카드 목록을 조회했을 때 syncStatus를 그대로 유지해야 한다")
    void getChannelCards_plannedSyncStatus_preserved() {
        SellerChannelCardDto dto = buildCard("AMAZON", "PLANNED", 0, 0, null);
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1")).willReturn(List.of(dto));

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("PLANNED");
    }

    @Test
    @DisplayName("통계 필드가 주어지면 채널 카드 목록을 조회했을 때 mapper 값을 그대로 유지해야 한다")
    void getChannelCards_statisticFields_passedThrough() {
        // 숫자/시각 통계 필드는 가공 없이 그대로 노출되어야 한다.
        LocalDateTime lastSync = LocalDateTime.of(2026, 3, 19, 9, 10);
        SellerChannelCardDto dto = buildCard("SHOPIFY", "ACTIVE", 14, 3, lastSync);
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1")).willReturn(List.of(dto));
        given(channelApiQueryService.findShopifyCredential("seller-1")).willReturn(buildCredential());
        given(shopifyPingClient.ping(any(), any())).willReturn(true);

        SellerChannelCardDto result = service.getChannelCards("seller-1").get(0);

        assertThat(result.getPendingOrders()).isEqualTo(14);
        assertThat(result.getTodayImported()).isEqualTo(3);
        assertThat(result.getLastSyncedAt()).isEqualTo(lastSync);
    }

    // ─────────────────────────────────────────────────────────
    // Shopify ping → syncStatus
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Shopify 자격 증명이 있고 ping에 성공하면 채널 카드 목록을 조회했을 때 syncStatus를 CONNECTED로 반환해야 한다")
    void getChannelCards_shopify_pingSuccess_returnsConnected() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(buildCard("SHOPIFY", "ACTIVE", 0, 0, null)));
        given(channelApiQueryService.findShopifyCredential("seller-1")).willReturn(buildCredential());
        given(shopifyPingClient.ping("test-store", "test-token")).willReturn(true);

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("Shopify 자격 증명이 있고 ping에 실패하면 채널 카드 목록을 조회했을 때 syncStatus를 DISCONNECTED로 반환해야 한다")
    void getChannelCards_shopify_pingFail_returnsDisconnected() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(buildCard("SHOPIFY", "ACTIVE", 0, 0, null)));
        given(channelApiQueryService.findShopifyCredential("seller-1")).willReturn(buildCredential());
        given(shopifyPingClient.ping("test-store", "test-token")).willReturn(false);

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("DISCONNECTED");
    }

    @Test
    @DisplayName("Shopify 자격 증명이 없으면 채널 카드 목록을 조회했을 때 syncStatus를 NOT_CONFIGURED로 반환해야 한다")
    void getChannelCards_shopify_noCredentials_returnsNotConfigured() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(buildCard("SHOPIFY", "PLANNED", 0, 0, null)));
        given(channelApiQueryService.findShopifyCredential("seller-1"))
                .willThrow(new BusinessException(ErrorCode.CHANNEL_CREDENTIALS_NOT_FOUND));

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("NOT_CONFIGURED");
        verifyNoInteractions(shopifyPingClient);
    }

    @Test
    @DisplayName("Amazon 채널이 주어지면 채널 카드 목록을 조회했을 때 ping을 호출하지 않고 syncStatus를 유지해야 한다")
    void getChannelCards_nonShopify_doesNotCallPing() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(buildCard("AMAZON", "PLANNED", 0, 0, null)));

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("PLANNED");
        verifyNoInteractions(shopifyPingClient);
        verifyNoInteractions(channelApiQueryService);
    }

    @Test
    @DisplayName("Shopify와 Amazon 채널이 함께 주어지면 채널 카드 목록을 조회했을 때 Shopify에만 ping을 한 번 호출해야 한다")
    void getChannelCards_mixed_onlyShopifyCallsPing() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(
                        buildCard("SHOPIFY", "ACTIVE", 2, 1, null),
                        buildCard("AMAZON", "PLANNED", 0, 0, null)));
        given(channelApiQueryService.findShopifyCredential("seller-1")).willReturn(buildCredential());
        given(shopifyPingClient.ping(any(), any())).willReturn(true);

        List<SellerChannelCardDto> result = service.getChannelCards("seller-1");

        assertThat(result.get(0).getSyncStatus()).isEqualTo("CONNECTED");
        assertThat(result.get(1).getSyncStatus()).isEqualTo("PLANNED");
        verify(shopifyPingClient, times(1)).ping(any(), any());
    }

    // ─────────────────────────────────────────────────────────
    // toLabel 매핑
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("SHOPIFY가 주어지면 표시 이름으로 변환했을 때 Shopify를 반환해야 한다")
    void toLabel_shopify() {
        assertThat(service.toLabel("SHOPIFY")).isEqualTo("Shopify");
    }

    @Test
    @DisplayName("AMAZON이 주어지면 표시 이름으로 변환했을 때 Amazon을 반환해야 한다")
    void toLabel_amazon() {
        assertThat(service.toLabel("AMAZON")).isEqualTo("Amazon");
    }

    @Test
    @DisplayName("MANUAL이 주어지면 표시 이름으로 변환했을 때 Manual을 반환해야 한다")
    void toLabel_manual() {
        assertThat(service.toLabel("MANUAL")).isEqualTo("Manual");
    }

    @Test
    @DisplayName("EXCEL이 주어지면 표시 이름으로 변환했을 때 Excel을 반환해야 한다")
    void toLabel_excel() {
        assertThat(service.toLabel("EXCEL")).isEqualTo("Excel");
    }

    @Test
    @DisplayName("알 수 없는 채널명이 주어지면 표시 이름으로 변환했을 때 원본 값을 반환해야 한다")
    void toLabel_unknown_returnsAsIs() {
        assertThat(service.toLabel("UNKNOWN_CHANNEL")).isEqualTo("UNKNOWN_CHANNEL");
    }

    @Test
    @DisplayName("채널명이 null이면 표시 이름으로 변환했을 때 빈 문자열을 반환해야 한다")
    void toLabel_null_returnsEmpty() {
        assertThat(service.toLabel(null)).isEqualTo("");
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("sellerId가 null이면 채널 카드 목록을 조회했을 때 BusinessException(INT-001)을 발생시키고 mapper를 호출하지 않아야 한다")
    void getChannelCards_throwsWhenSellerIdNull() {
        assertThatThrownBy(() -> service.getChannelCards(null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(channelCardMapper);
    }

    @Test
    @DisplayName("sellerId가 공백이면 채널 카드 목록을 조회했을 때 BusinessException(INT-001)을 발생시키고 mapper를 호출하지 않아야 한다")
    void getChannelCards_throwsWhenSellerIdBlank() {
        assertThatThrownBy(() -> service.getChannelCards("   "))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(channelCardMapper);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    // mapper 응답 fixture를 짧게 재사용하기 위한 헬퍼다.
    private SellerChannelCardDto buildCard(String key, String syncStatus,
                                           int pendingOrders, int todayImported,
                                           LocalDateTime lastSyncedAt) {
        SellerChannelCardDto dto = new SellerChannelCardDto();
        dto.setKey(key);
        dto.setSyncStatus(syncStatus);
        dto.setPendingOrders(pendingOrders);
        dto.setTodayImported(todayImported);
        dto.setLastSyncedAt(lastSyncedAt);
        return dto;
    }

    // Shopify 자격증명 fixture 헬퍼다.
    private ShopifyCredentialDto buildCredential() {
        ShopifyCredentialDto cred = new ShopifyCredentialDto();
        cred.setStoreName("test-store");
        cred.setAccessToken("test-token");
        return cred;
    }
}
