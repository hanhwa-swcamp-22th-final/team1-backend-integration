package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.exception.BusinessException;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerChannelImportPreviewService 테스트")
class SellerChannelImportPreviewServiceTest {

    private static final String SELLER_ID = "seller-001";

    @Mock
    private ChannelImportPreviewProvider previewProvider;

    private SellerChannelImportPreviewService service;

    @BeforeEach
    void setUp() {
        service = new SellerChannelImportPreviewService(List.of(previewProvider));
    }

    @Test
    @DisplayName("지원하는 provider가 있으면 미리보기를 수행했을 때 해당 provider 결과를 그대로 반환해야 한다")
    void preview_delegatesToMatchedProvider() {
        SellerChannelImportPreviewRequest request = new SellerChannelImportPreviewRequest(
                "Shopify KR Store",
                "ops@example.com",
                "최근 7일",
                true);
        SellerChannelImportPreviewResponse expected =
                new SellerChannelImportPreviewResponse(4, LocalDateTime.of(2026, 4, 11, 9, 0));

        given(previewProvider.supports(OrderChannel.SHOPIFY)).willReturn(true);
        given(previewProvider.preview(SELLER_ID, request)).willReturn(expected);

        SellerChannelImportPreviewResponse result = service.preview(SELLER_ID, OrderChannel.SHOPIFY, request);

        assertThat(result).isSameAs(expected);
        verify(previewProvider).preview(SELLER_ID, request);
    }

    @Test
    @DisplayName("지원하는 provider가 없으면 미리보기를 수행했을 때 BusinessException을 발생시켜야 한다")
    void preview_throwsWhenChannelUnsupported() {
        given(previewProvider.supports(OrderChannel.AMAZON)).willReturn(false);

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
}
