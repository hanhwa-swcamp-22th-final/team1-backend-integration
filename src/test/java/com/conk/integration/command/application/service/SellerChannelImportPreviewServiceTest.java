package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.ShopifyOrderClient;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.query.dto.ShopifyCredentialDto;
import com.conk.integration.query.service.ChannelApiQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerChannelImportPreviewService 테스트")
class SellerChannelImportPreviewServiceTest {

    private static final String SELLER_ID = "seller-001";

    @Mock
    private ChannelApiQueryService channelApiQueryService;

    @Mock
    private ChannelOrderRepository channelOrderRepository;

    @Mock
    private ShopifyOrderClient shopifyOrderClient;

    @InjectMocks
    private SellerChannelImportPreviewService service;

    @Test
    @DisplayName("최근 저장 시각이 있으면 미리보기를 수행했을 때 해당 createdAt 이후로 Shopify 주문을 조회해야 한다")
    void preview_usesLatestCreatedAtAsSince() {
        LocalDateTime lastCreatedAt = LocalDateTime.of(2026, 4, 11, 9, 0);
        SellerChannelImportPreviewRequest request = new SellerChannelImportPreviewRequest(
                "Shopify KR Store",
                "ops@example.com",
                "최근 7일",
                true);
        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(channelOrderRepository.findFirstBySellerIdAndOrderChannelOrderByAuditCreatedAtDesc(
                SELLER_ID, OrderChannel.SHOPIFY)).willReturn(Optional.of(buildChannelOrder(lastCreatedAt)));
        given(shopifyOrderClient.countOrdersSince(anyString(), anyString(), any())).willReturn(2);

        SellerChannelImportPreviewResponse response = service.preview(SELLER_ID, OrderChannel.SHOPIFY, request);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(shopifyOrderClient).should().countOrdersSince(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(lastCreatedAt);
        assertThat(response.getPendingOrders()).isEqualTo(2);
        assertThat(response.getLastSyncedAt()).isEqualTo(lastCreatedAt);
        then(channelOrderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("최근 저장 시각이 없으면 미리보기를 수행했을 때 현재 시각에서 syncWindow를 뺀 시각 이후로 Shopify 주문을 조회해야 한다")
    void preview_usesNowMinusSyncWindowWhenLastCreatedAtMissing() {
        LocalDateTime before = LocalDateTime.now();
        SellerChannelImportPreviewRequest request = new SellerChannelImportPreviewRequest(
                null,
                null,
                "최근 3일",
                true);
        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(channelOrderRepository.findFirstBySellerIdAndOrderChannelOrderByAuditCreatedAtDesc(
                SELLER_ID, OrderChannel.SHOPIFY)).willReturn(Optional.empty());
        given(shopifyOrderClient.countOrdersSince(anyString(), anyString(), any())).willReturn(0);

        SellerChannelImportPreviewResponse response = service.preview(SELLER_ID, OrderChannel.SHOPIFY, request);

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(shopifyOrderClient).should().countOrdersSince(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(before.minusDays(3));
        assertThat(captor.getValue()).isBeforeOrEqualTo(after.minusDays(3));
        assertThat(response.getPendingOrders()).isZero();
        assertThat(response.getLastSyncedAt()).isNull();
    }

    @Test
    @DisplayName("알 수 없는 syncWindow가 주어지면 미리보기를 수행했을 때 기본 7일 범위를 사용해야 한다")
    void preview_usesDefaultSevenDaysWhenSyncWindowUnknown() {
        LocalDateTime before = LocalDateTime.now();
        SellerChannelImportPreviewRequest request = new SellerChannelImportPreviewRequest(
                null,
                null,
                "최근 30일",
                false);
        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(channelOrderRepository.findFirstBySellerIdAndOrderChannelOrderByAuditCreatedAtDesc(
                SELLER_ID, OrderChannel.SHOPIFY)).willReturn(Optional.empty());
        given(shopifyOrderClient.countOrdersSince(anyString(), anyString(), any())).willReturn(0);

        service.preview(SELLER_ID, OrderChannel.SHOPIFY, request);

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(shopifyOrderClient).should().countOrdersSince(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(before.minusDays(7));
        assertThat(captor.getValue()).isBeforeOrEqualTo(after.minusDays(7));
    }

    @Test
    @DisplayName("지원하지 않는 채널이 주어지면 미리보기를 수행했을 때 BusinessException을 발생시켜야 한다")
    void preview_throwsWhenChannelUnsupported() {
        assertThatThrownBy(() -> service.preview(SELLER_ID, OrderChannel.AMAZON, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("지원하지 않는 주문 동기화 채널입니다: AMAZON");
    }

    @Test
    @DisplayName("sellerId가 비어 있으면 미리보기를 수행했을 때 BusinessException을 발생시켜야 한다")
    void preview_throwsWhenSellerIdBlank() {
        assertThatThrownBy(() -> service.preview(" ", OrderChannel.SHOPIFY, null))
                .isInstanceOf(BusinessException.class);
    }

    private ShopifyCredentialDto buildCredential() {
        ShopifyCredentialDto dto = new ShopifyCredentialDto();
        dto.setStoreName("conktest");
        dto.setAccessToken("test-token");
        return dto;
    }

    private ChannelOrder buildChannelOrder(LocalDateTime createdAt) {
        return ChannelOrder.builder()
                .orderId("1001")
                .channelOrderNo("#1001")
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId(SELLER_ID)
                .audit(AuditFields.builder().createdAt(createdAt).updatedAt(createdAt).build())
                .build();
    }
}
