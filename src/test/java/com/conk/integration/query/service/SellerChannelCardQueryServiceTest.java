package com.conk.integration.query.service;

import com.conk.integration.query.dto.SellerChannelCardDto;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 채널 카드 조회 서비스의 입력 검증과 라벨 후처리를 검증한다.
@ExtendWith(MockitoExtension.class)
@DisplayName("query.SellerChannelCardQueryService 단위 테스트")
class SellerChannelCardQueryServiceTest {

    @Mock private SellerChannelCardMapper channelCardMapper;
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
    @DisplayName("[GREEN] ACTIVE syncStatus mapper 값 그대로 통과")
    void getChannelCards_activeSyncStatus_preserved() {
        SellerChannelCardDto dto = buildCard("SHOPIFY", "ACTIVE", 0, 0, null);
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

        SellerChannelCardDto result = service.getChannelCards("seller-1").get(0);

        assertThat(result.getPendingOrders()).isEqualTo(14);
        assertThat(result.getTodayImported()).isEqualTo(3);
        assertThat(result.getLastSyncedAt()).isEqualTo(lastSync);
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
    @DisplayName("[예외] sellerId null → IllegalArgumentException, mapper 미호출")
    void getChannelCards_throwsWhenSellerIdNull() {
        assertThatThrownBy(() -> service.getChannelCards(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelCardMapper);
    }

    @Test
    @DisplayName("[예외] sellerId 공백 → IllegalArgumentException, mapper 미호출")
    void getChannelCards_throwsWhenSellerIdBlank() {
        assertThatThrownBy(() -> service.getChannelCards("   "))
                .isInstanceOf(IllegalArgumentException.class);
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
}
