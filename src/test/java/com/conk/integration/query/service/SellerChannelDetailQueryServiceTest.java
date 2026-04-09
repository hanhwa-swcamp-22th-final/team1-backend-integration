package com.conk.integration.query.service;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.dto.SellerChannelDetailDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("query.SellerChannelDetailQueryService 단위 테스트")
class SellerChannelDetailQueryServiceTest {

    @Mock private ChannelApiQueryService channelApiQueryService;
    @Mock private ShopifyPingClient shopifyPingClient;
    @InjectMocks private SellerChannelDetailQueryService service;

    @Test
    @DisplayName("[GREEN] SHOPIFY + ping 성공 → 계약 DTO 반환")
    void getChannelDetail_shopifyConnected_returnsDetail() {
        ChannelApi channelApi = buildChannelApi("seller-A", "SHOPIFY", "shpat_xxxxxxxx", "my-shopify-store");
        given(channelApiQueryService.findChannelApi("seller-A", "SHOPIFY")).willReturn(channelApi);
        given(shopifyPingClient.ping("my-shopify-store", "shpat_xxxxxxxx")).willReturn(true);

        SellerChannelDetailDto result = service.getChannelDetail("seller-A", "shopify");

        assertThat(result.getChannelName()).isEqualTo("SHOPIFY");
        assertThat(result.getStoreName()).isEqualTo("my-shopify-store");
        assertThat(result.getChannelApi()).isEqualTo("shpat_xxxxxxxx");
        assertThat(result.getConnectedAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 9, 0));
    }

    @Test
    @DisplayName("[GREEN] 비-Shopify 채널은 ping 없이 상세 DTO를 반환한다")
    void getChannelDetail_nonShopify_returnsDetailWithoutPing() {
        ChannelApi channelApi = buildChannelApi("seller-A", "AMAZON", "amazon-token", "amazon-store");
        given(channelApiQueryService.findChannelApi("seller-A", "AMAZON")).willReturn(channelApi);

        SellerChannelDetailDto result = service.getChannelDetail("seller-A", "AMAZON");

        assertThat(result.getChannelName()).isEqualTo("AMAZON");
        assertThat(result.getStoreName()).isEqualTo("amazon-store");
        verifyNoInteractions(shopifyPingClient);
    }

    @Test
    @DisplayName("[예외] SHOPIFY ping 실패 → 연결 정보 없음")
    void getChannelDetail_shopifyDisconnected_throwsNotFound() {
        ChannelApi channelApi = buildChannelApi("seller-A", "SHOPIFY", "shpat_xxxxxxxx", "my-shopify-store");
        given(channelApiQueryService.findChannelApi("seller-A", "SHOPIFY")).willReturn(channelApi);
        given(shopifyPingClient.ping("my-shopify-store", "shpat_xxxxxxxx")).willReturn(false);

        assertThatThrownBy(() -> service.getChannelDetail("seller-A", "SHOPIFY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("[예외] sellerId 공백 → INT-001")
    void getChannelDetail_blankSellerId_throwsInvalidSellerId() {
        assertThatThrownBy(() -> service.getChannelDetail("   ", "SHOPIFY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SELLER_ID);

        verifyNoInteractions(channelApiQueryService);
    }

    @Test
    @DisplayName("[예외] channelKey 공백 → 연결 정보 없음")
    void getChannelDetail_blankChannelKey_throwsNotFound() {
        assertThatThrownBy(() -> service.getChannelDetail("seller-A", "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);

        verifyNoInteractions(channelApiQueryService);
    }

    @Test
    @DisplayName("[GREEN] 채널 키는 대문자로 정규화되어 조회된다")
    void getChannelDetail_normalizesChannelKeyToUpperCase() {
        ChannelApi channelApi = buildChannelApi("seller-A", "SHOPIFY", "shpat_xxxxxxxx", "my-shopify-store");
        given(channelApiQueryService.findChannelApi("seller-A", "SHOPIFY")).willReturn(channelApi);
        given(shopifyPingClient.ping("my-shopify-store", "shpat_xxxxxxxx")).willReturn(true);

        service.getChannelDetail("seller-A", "shopify");

        verify(channelApiQueryService).findChannelApi("seller-A", "SHOPIFY");
    }

    private ChannelApi buildChannelApi(String sellerId, String channelName, String token, String storeName) {
        return ChannelApi.builder()
                .id(new ChannelApiId(sellerId, channelName))
                .channelApi(token)
                .storeName(storeName)
                .audit(AuditFields.builder()
                        .createdAt(LocalDateTime.of(2026, 1, 15, 9, 0))
                        .build())
                .build();
    }
}
