package com.conk.integration.query.service;

import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.dto.SellerChannelCardDto;
import com.conk.integration.query.dto.ShopifyCredentialDto;
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
@DisplayName("query.SellerChannelCardQueryService 단위 테스트")
class SellerChannelCardQueryServiceTest {

    @Mock private SellerChannelCardMapper channelCardMapper;
    @Mock private ChannelApiQueryService channelApiQueryService;
    @Mock private ShopifyPingClient shopifyPingClient;
    @InjectMocks private SellerChannelCardQueryService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] mapper 결과 반환 후 label 채움")
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
    @DisplayName("[GREEN] 빈 결과 → 빈 리스트 반환")
    void getChannelCards_returnsEmpty_whenMapperReturnsEmpty() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1")).willReturn(List.of());

        List<SellerChannelCardDto> result = service.getChannelCards("seller-1");

        assertThat(result).isEmpty();
        verify(channelCardMapper).findBySellerIdGroupedByChannel("seller-1");
    }

    @Test
    @DisplayName("[GREEN] ACTIVE syncStatus mapper 값 그대로 통과 (SHOPIFY 외 채널)")
    void getChannelCards_activeSyncStatus_preserved() {
        // SHOPIFY 이외 채널은 ping 없이 DB 값을 그대로 반환해야 한다.
        SellerChannelCardDto dto = buildCard("AMAZON", "ACTIVE", 0, 0, null);
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1")).willReturn(List.of(dto));

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[GREEN] PLANNED syncStatus mapper 값 그대로 통과")
    void getChannelCards_plannedSyncStatus_preserved() {
        SellerChannelCardDto dto = buildCard("AMAZON", "PLANNED", 0, 0, null);
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1")).willReturn(List.of(dto));

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("PLANNED");
    }

    @Test
    @DisplayName("[GREEN] pendingOrders/todayImported/lastSyncedAt mapper 값 그대로")
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
    @DisplayName("[GREEN] SHOPIFY + ping 성공 → syncStatus = CONNECTED")
    void getChannelCards_shopify_pingSuccess_returnsConnected() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(buildCard("SHOPIFY", "ACTIVE", 0, 0, null)));
        given(channelApiQueryService.findShopifyCredential("seller-1")).willReturn(buildCredential());
        given(shopifyPingClient.ping("test-store", "test-token")).willReturn(true);

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("[GREEN] SHOPIFY + ping 실패 → syncStatus = DISCONNECTED")
    void getChannelCards_shopify_pingFail_returnsDisconnected() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(buildCard("SHOPIFY", "ACTIVE", 0, 0, null)));
        given(channelApiQueryService.findShopifyCredential("seller-1")).willReturn(buildCredential());
        given(shopifyPingClient.ping("test-store", "test-token")).willReturn(false);

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("DISCONNECTED");
    }

    @Test
    @DisplayName("[GREEN] SHOPIFY + 자격증명 없음 → syncStatus = NOT_CONFIGURED")
    void getChannelCards_shopify_noCredentials_returnsNotConfigured() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(buildCard("SHOPIFY", "PLANNED", 0, 0, null)));
        given(channelApiQueryService.findShopifyCredential("seller-1"))
                .willThrow(new BusinessException(ErrorCode.CHANNEL_CREDENTIALS_NOT_FOUND));

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("NOT_CONFIGURED");
        verifyNoInteractions(shopifyPingClient);
    }

    @Test
    @DisplayName("[GREEN] AMAZON 채널은 ping 미호출, DB syncStatus 유지")
    void getChannelCards_nonShopify_doesNotCallPing() {
        given(channelCardMapper.findBySellerIdGroupedByChannel("seller-1"))
                .willReturn(List.of(buildCard("AMAZON", "PLANNED", 0, 0, null)));

        assertThat(service.getChannelCards("seller-1").get(0).getSyncStatus()).isEqualTo("PLANNED");
        verifyNoInteractions(shopifyPingClient);
        verifyNoInteractions(channelApiQueryService);
    }

    @Test
    @DisplayName("[GREEN] SHOPIFY + AMAZON 혼합 시 SHOPIFY만 ping 1회 호출")
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
    @DisplayName("[GREEN] toLabel: SHOPIFY → 'Shopify'")
    void toLabel_shopify() {
        assertThat(service.toLabel("SHOPIFY")).isEqualTo("Shopify");
    }

    @Test
    @DisplayName("[GREEN] toLabel: AMAZON → 'Amazon'")
    void toLabel_amazon() {
        assertThat(service.toLabel("AMAZON")).isEqualTo("Amazon");
    }

    @Test
    @DisplayName("[GREEN] toLabel: MANUAL → 'Manual'")
    void toLabel_manual() {
        assertThat(service.toLabel("MANUAL")).isEqualTo("Manual");
    }

    @Test
    @DisplayName("[GREEN] toLabel: EXCEL → 'Excel'")
    void toLabel_excel() {
        assertThat(service.toLabel("EXCEL")).isEqualTo("Excel");
    }

    @Test
    @DisplayName("[GREEN] toLabel: 알 수 없는 값 → 원본 그대로")
    void toLabel_unknown_returnsAsIs() {
        assertThat(service.toLabel("UNKNOWN_CHANNEL")).isEqualTo("UNKNOWN_CHANNEL");
    }

    @Test
    @DisplayName("[GREEN] toLabel: null → 빈 문자열")
    void toLabel_null_returnsEmpty() {
        assertThat(service.toLabel(null)).isEqualTo("");
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] sellerId null → BusinessException(INT-001), mapper 미호출")
    void getChannelCards_throwsWhenSellerIdNull() {
        assertThatThrownBy(() -> service.getChannelCards(null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(channelCardMapper);
    }

    @Test
    @DisplayName("[예외] sellerId 공백 → BusinessException(INT-001), mapper 미호출")
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
