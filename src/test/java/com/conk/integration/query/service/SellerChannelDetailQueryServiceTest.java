package com.conk.integration.query.service;

import com.conk.integration.common.channel.ChannelConnectionVerifier;
import com.conk.integration.common.channel.dto.ChannelConnectionInfo;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.common.channel.dto.SellerChannelDetailDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerChannelDetailQueryService 테스트")
class SellerChannelDetailQueryServiceTest {

    @Mock private ChannelApiQueryService channelApiQueryService;
    @Mock private ChannelConnectionVerifier connectionVerifier;
    private SellerChannelDetailQueryService service;

    @BeforeEach
    void setUp() {
        service = new SellerChannelDetailQueryService(channelApiQueryService, List.of(connectionVerifier));
    }

    @Test
    @DisplayName("Shopify 자격 증명이 있고 ping에 성공하면 채널 상세를 조회했을 때 계약 DTO를 반환해야 한다")
        void getChannelDetail_shopifyConnected_returnsDetail() {
        ChannelConnectionInfo connectionInfo = buildConnectionInfo("SHOPIFY", "shpat_xxxxxxxx", "my-shopify-store", LocalDateTime.of(2026, 1, 15, 9, 0));
        given(channelApiQueryService.findChannelConnectionInfo("seller-A", "SHOPIFY")).willReturn(connectionInfo);
        given(connectionVerifier.supports("SHOPIFY")).willReturn(true);
        given(connectionVerifier.verify("my-shopify-store", "shpat_xxxxxxxx")).willReturn(true);

        SellerChannelDetailDto result = service.getChannelDetail("seller-A", "shopify");

        assertThat(result.getChannelName()).isEqualTo("SHOPIFY");
        assertThat(result.getStoreName()).isEqualTo("my-shopify-store");
        assertThat(result.getChannelApi()).isEqualTo("shpat_xxxxxxxx");
        assertThat(result.getConnectedAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 9, 0));
    }

    @Test
    @DisplayName("Shopify가 아닌 채널이 주어지면 채널 상세를 조회했을 때 ping 없이 상세 DTO를 반환해야 한다")
    void getChannelDetail_nonShopify_returnsDetailWithoutPing() {
        ChannelConnectionInfo connectionInfo = buildConnectionInfo("AMAZON", "amazon-token", "amazon-store", LocalDateTime.of(2026, 1, 15, 9, 0));
        given(channelApiQueryService.findChannelConnectionInfo("seller-A", "AMAZON")).willReturn(connectionInfo);

        SellerChannelDetailDto result = service.getChannelDetail("seller-A", "AMAZON");

        assertThat(result.getChannelName()).isEqualTo("AMAZON");
        assertThat(result.getStoreName()).isEqualTo("amazon-store");
        verify(connectionVerifier).supports("AMAZON");
    }

    @Test
    @DisplayName("Shopify ping에 실패하면 채널 상세를 조회했을 때 연결 정보 없음 예외를 발생시켜야 한다")
    void getChannelDetail_shopifyDisconnected_throwsNotFound() {
        ChannelConnectionInfo connectionInfo = buildConnectionInfo("SHOPIFY", "shpat_xxxxxxxx", "my-shopify-store", LocalDateTime.of(2026, 1, 15, 9, 0));
        given(channelApiQueryService.findChannelConnectionInfo("seller-A", "SHOPIFY")).willReturn(connectionInfo);
        given(connectionVerifier.supports("SHOPIFY")).willReturn(true);
        given(connectionVerifier.verify("my-shopify-store", "shpat_xxxxxxxx")).willReturn(false);

        assertThatThrownBy(() -> service.getChannelDetail("seller-A", "SHOPIFY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("sellerId가 공백이면 채널 상세를 조회했을 때 BusinessException(INT-001)을 발생시켜야 한다")
    void getChannelDetail_blankSellerId_throwsInvalidSellerId() {
        assertThatThrownBy(() -> service.getChannelDetail("   ", "SHOPIFY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SELLER_ID);

        verifyNoInteractions(channelApiQueryService);
    }

    @Test
    @DisplayName("channelKey가 공백이면 채널 상세를 조회했을 때 연결 정보 없음 예외를 발생시켜야 한다")
    void getChannelDetail_blankChannelKey_throwsNotFound() {
        assertThatThrownBy(() -> service.getChannelDetail("seller-A", "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);

        verifyNoInteractions(channelApiQueryService);
    }

    @Test
    @DisplayName("소문자 채널 키가 주어지면 채널 상세를 조회했을 때 대문자로 정규화해 조회해야 한다")
    void getChannelDetail_normalizesChannelKeyToUpperCase() {
        ChannelConnectionInfo connectionInfo = buildConnectionInfo("SHOPIFY", "shpat_xxxxxxxx", "my-shopify-store", LocalDateTime.of(2026, 1, 15, 9, 0));
        given(channelApiQueryService.findChannelConnectionInfo("seller-A", "SHOPIFY")).willReturn(connectionInfo);
        given(connectionVerifier.supports("SHOPIFY")).willReturn(true);
        given(connectionVerifier.verify("my-shopify-store", "shpat_xxxxxxxx")).willReturn(true);

        service.getChannelDetail("seller-A", "shopify");

        verify(channelApiQueryService).findChannelConnectionInfo("seller-A", "SHOPIFY");
    }

    @Test
    @DisplayName("audit가 null이면 채널 상세를 조회했을 때 connectedAt을 null로 반환해야 한다")
    void getChannelDetail_returnsNullConnectedAtWhenAuditIsNull() {
        ChannelConnectionInfo connectionInfo = buildConnectionInfo("AMAZON", "amazon-token", "amazon-store", null);
        given(channelApiQueryService.findChannelConnectionInfo("seller-A", "AMAZON")).willReturn(connectionInfo);

        SellerChannelDetailDto result = service.getChannelDetail("seller-A", "AMAZON");

        assertThat(result.getConnectedAt()).isNull();
    }

    @Test
    @DisplayName("ChannelApiQueryService에서 예외가 발생하면 채널 상세를 조회했을 때 호출자에게 그대로 전파해야 한다")
    void getChannelDetail_propagatesWhenChannelApiQueryServiceThrows() {
        given(channelApiQueryService.findChannelConnectionInfo("seller-A", "SHOPIFY"))
                .willThrow(new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND));

        assertThatThrownBy(() -> service.getChannelDetail("seller-A", "SHOPIFY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("channelKey가 null이면 채널 상세를 조회했을 때 연결 정보 없음 예외를 발생시켜야 한다")
    void getChannelDetail_nullChannelKey_throwsNotFound() {
        assertThatThrownBy(() -> service.getChannelDetail("seller-A", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
    }

    private ChannelConnectionInfo buildConnectionInfo(String channelName, String token, String storeName, LocalDateTime connectedAt) {
        return new ChannelConnectionInfo(channelName, storeName, token, connectedAt);
    }
}


