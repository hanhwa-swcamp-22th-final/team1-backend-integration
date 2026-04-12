package com.conk.integration.query.service;

import com.conk.integration.common.channel.dto.ChannelConnectionInfo;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.query.mapper.ChannelApiMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelApiQueryService 테스트")
class ChannelApiQueryServiceTest {

    @Mock
    private ChannelApiMapper channelApiMapper;

    @InjectMocks
    private ChannelApiQueryService service;

    @Test
    @DisplayName("Shopify 자격 증명이 존재하면 자격 증명을 조회했을 때 DTO를 반환해야 한다")
    void findShopifyCredential_returnsCredentialWhenExists() {
        ChannelCredential credential = new ChannelCredential();
        credential.setStoreName("test-store");
        credential.setAccessToken("test-token");
        given(channelApiMapper.findChannelCredential("seller-001", "SHOPIFY")).willReturn(credential);

        ChannelCredential result = service.findChannelCredential("seller-001", "SHOPIFY");

        assertThat(result.getStoreName()).isEqualTo("test-store");
        assertThat(result.getAccessToken()).isEqualTo("test-token");
    }

    @Test
    @DisplayName("Shopify 자격 증명이 없으면 자격 증명을 조회했을 때 BusinessException(INT-103)을 발생시켜야 한다")
    void findShopifyCredential_throwsWhenCredentialNotFound() {
        given(channelApiMapper.findChannelCredential("seller-001", "SHOPIFY")).willReturn(null);

        assertThatThrownBy(() -> service.findChannelCredential("seller-001", "SHOPIFY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CREDENTIALS_NOT_FOUND);
    }

    @Test
    @DisplayName("채널 연결 정보가 존재하면 채널 연결 정보를 조회했을 때 read model을 반환해야 한다")
    void findChannelConnectionInfo_returnsConnectionInfoWhenExists() {
        ChannelConnectionInfo connectionInfo = new ChannelConnectionInfo(
                "SHOPIFY",
                "store-name",
                "token-value",
                null);
        given(channelApiMapper.findConnectionInfo("seller-001", "SHOPIFY")).willReturn(connectionInfo);

        ChannelConnectionInfo result = service.findChannelConnectionInfo("seller-001", "SHOPIFY");

        assertThat(result.getChannelName()).isEqualTo("SHOPIFY");
        assertThat(result.getStoreName()).isEqualTo("store-name");
    }

    @Test
    @DisplayName("채널 연결 정보가 없으면 채널 연결 정보를 조회했을 때 BusinessException(INT-404)를 발생시켜야 한다")
    void findChannelConnectionInfo_throwsWhenChannelConnectionNotFound() {
        given(channelApiMapper.findConnectionInfo("seller-001", "SHOPIFY")).willReturn(null);

        assertThatThrownBy(() -> service.findChannelConnectionInfo("seller-001", "SHOPIFY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
    }
}

