package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelConnectRequest;
import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import com.conk.integration.command.infrastructure.repository.ChannelApiRepository;
import com.conk.integration.common.channel.ChannelConnectionVerifier;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerChannelConnectService 테스트")
class SellerChannelConnectServiceTest {

    @Mock
    private ChannelApiRepository channelApiRepository;

    @Mock
    private ChannelConnectionVerifier connectionVerifier;

    private SellerChannelConnectService service;

    @BeforeEach
    void setUp() {
        service = new SellerChannelConnectService(channelApiRepository, List.of(connectionVerifier));
    }

    @Test
    @DisplayName("유효한 sellerId와 Shopify 연결 정보가 주어지면 채널 연결을 수행했을 때 신규 ChannelApi를 저장해야 한다")
    void connect_validRequest_savesNewChannelApi() {
        SellerChannelConnectRequest request = new SellerChannelConnectRequest(
                "my-shopify-store", "shpat_token", "Shopify KR Store", "ops@example.com", "AUTO");
        given(connectionVerifier.supports("SHOPIFY")).willReturn(true);
        given(connectionVerifier.verify("my-shopify-store", "shpat_token")).willReturn(true);
        given(channelApiRepository.findById(new ChannelApiId("seller-001", "SHOPIFY"))).willReturn(Optional.empty());
        given(channelApiRepository.saveAndFlush(any(ChannelApi.class))).willAnswer(invocation -> {
            ChannelApi entity = invocation.getArgument(0);
            if (entity.getAudit().getCreatedAt() == null) {
                entity.getAudit().setCreatedAt(LocalDateTime.of(2026, 4, 10, 11, 0));
            }
            return entity;
        });

        SellerChannelDetailDto result = service.connect("seller-001", "shopify", request);

        assertThat(result.getChannelName()).isEqualTo("SHOPIFY");
        assertThat(result.getStoreName()).isEqualTo("my-shopify-store");
        assertThat(result.getChannelApi()).isEqualTo("shpat_token");
        verify(channelApiRepository).saveAndFlush(any(ChannelApi.class));
    }

    @Test
    @DisplayName("동일 sellerId와 channelKey의 연결 정보가 이미 있으면 채널 연결을 수행했을 때 기존 ChannelApi를 갱신해야 한다")
    void connect_existingConnection_updatesChannelApi() {
        ChannelApi existing = ChannelApi.builder()
                .id(new ChannelApiId("seller-001", "SHOPIFY"))
                .channelApi("old-token")
                .storeName("old-store")
                .audit(AuditFields.builder().createdAt(LocalDateTime.of(2026, 4, 1, 9, 0)).build())
                .build();
        SellerChannelConnectRequest request = new SellerChannelConnectRequest(
                "new-store", "new-token", "Shopify KR Store", "ops@example.com", "AUTO");

        given(connectionVerifier.supports("SHOPIFY")).willReturn(true);
        given(connectionVerifier.verify("new-store", "new-token")).willReturn(true);
        given(channelApiRepository.findById(new ChannelApiId("seller-001", "SHOPIFY"))).willReturn(Optional.of(existing));
        given(channelApiRepository.saveAndFlush(existing)).willReturn(existing);

        SellerChannelDetailDto result = service.connect("seller-001", "SHOPIFY", request);

        assertThat(existing.getStoreName()).isEqualTo("new-store");
        assertThat(existing.getChannelApi()).isEqualTo("new-token");
        assertThat(result.getConnectedAt()).isEqualTo(LocalDateTime.of(2026, 4, 1, 9, 0));
    }

    @Test
    @DisplayName("sellerId가 공백이면 채널 연결을 수행했을 때 BusinessException(INT-001)을 발생시키고 repository를 호출하지 않아야 한다")
    void connect_blankSellerId_throwsInvalidSellerId() {
        assertThatThrownBy(() -> service.connect("   ", "SHOPIFY", validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SELLER_ID);

        verifyNoInteractions(channelApiRepository);
    }

    @Test
    @DisplayName("channelKey가 공백이면 채널 연결을 수행했을 때 BusinessException(INT-004)을 발생시켜야 한다")
    void connect_blankChannelKey_throwsUnsupportedChannel() {
        assertThatThrownBy(() -> service.connect("seller-001", "   ", validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CHANNEL);
    }

    @Test
    @DisplayName("SHOPIFY가 아닌 채널 키가 주어지면 채널 연결을 수행했을 때 BusinessException(INT-004)을 발생시켜야 한다")
    void connect_unsupportedChannel_throwsUnsupportedChannel() {
        assertThatThrownBy(() -> service.connect("seller-001", "AMAZON", validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CHANNEL);

        verifyNoInteractions(channelApiRepository);
    }

    @Test
    @DisplayName("storeName이 없으면 채널 연결을 수행했을 때 BusinessException(INT-001)을 발생시켜야 한다")
    void connect_blankStoreName_throwsInvalidSellerId() {
        SellerChannelConnectRequest request = new SellerChannelConnectRequest(
                " ", "shpat_token", "Shopify KR Store", "ops@example.com", "AUTO");

        assertThatThrownBy(() -> service.connect("seller-001", "SHOPIFY", request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("storeName는 필수입니다.");

        verifyNoInteractions(channelApiRepository);
    }

    @Test
    @DisplayName("channelApi가 없으면 채널 연결을 수행했을 때 BusinessException(INT-001)을 발생시켜야 한다")
    void connect_blankChannelApi_throwsInvalidSellerId() {
        SellerChannelConnectRequest request = new SellerChannelConnectRequest(
                "my-shopify-store", " ", "Shopify KR Store", "ops@example.com", "AUTO");

        assertThatThrownBy(() -> service.connect("seller-001", "SHOPIFY", request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("channelApi는 필수입니다.");

        verifyNoInteractions(channelApiRepository);
    }

    @Test
    @DisplayName("Shopify 연결 검증에 실패하면 채널 연결을 수행했을 때 저장하지 않고 BusinessException(INT-404)를 발생시켜야 한다")
    void connect_pingFails_throwsNotFound() {
        SellerChannelConnectRequest request = validRequest();
        given(connectionVerifier.supports("SHOPIFY")).willReturn(true);
        given(connectionVerifier.verify("my-shopify-store", "shpat_token")).willReturn(false);

        assertThatThrownBy(() -> service.connect("seller-001", "SHOPIFY", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);

        verifyNoInteractions(channelApiRepository);
    }

    private SellerChannelConnectRequest validRequest() {
        return new SellerChannelConnectRequest(
                "my-shopify-store", "shpat_token", "Shopify KR Store", "ops@example.com", "AUTO");
    }
}


