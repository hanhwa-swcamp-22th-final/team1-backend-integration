package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelOrderSyncDispatchService 테스트")
class ChannelOrderSyncDispatchServiceTest {

    @Mock
    private ChannelOrderSyncer shopifySyncer;

    @Mock
    private ChannelOrderSyncer amazonSyncer;

    @Test
    @DisplayName("지원하는 채널이 주어지면 주문 동기화를 수행했을 때 해당 syncer를 호출해야 한다")
    void sync_callsMatchedSyncer() {
        ChannelOrderSyncDispatchService service =
                new ChannelOrderSyncDispatchService(List.of(shopifySyncer, amazonSyncer));
        ChannelOrderSyncResponse response = new ChannelOrderSyncResponse(1, 0, List.of());

        given(shopifySyncer.supports(OrderChannel.SHOPIFY)).willReturn(true);
        given(shopifySyncer.syncOrders("seller-001")).willReturn(response);

        ChannelOrderSyncResponse result = service.sync("seller-001", OrderChannel.SHOPIFY);

        assertThat(result.getSavedCount()).isEqualTo(1);
        then(shopifySyncer).should().syncOrders("seller-001");
        then(amazonSyncer).should(never()).syncOrders("seller-001");
    }

    @Test
    @DisplayName("지원하는 syncer가 없으면 주문 동기화를 수행했을 때 BusinessException(INT-004)를 발생시켜야 한다")
    void sync_throwsWhenSyncerNotSupported() {
        ChannelOrderSyncDispatchService service =
                new ChannelOrderSyncDispatchService(List.of(shopifySyncer, amazonSyncer));

        given(shopifySyncer.supports(OrderChannel.AMAZON)).willReturn(false);
        given(amazonSyncer.supports(OrderChannel.AMAZON)).willReturn(false);

        assertThatThrownBy(() -> service.sync("seller-001", OrderChannel.AMAZON))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CHANNEL);
    }

    @Test
    @DisplayName("판매자 ID가 주어지면 주문 동기화를 수행했을 때 선택된 syncer에 판매자 ID를 그대로 전달해야 한다")
    void sync_passesSellerIdToSelectedSyncer() {
        ChannelOrderSyncDispatchService service =
                new ChannelOrderSyncDispatchService(List.of(shopifySyncer));

        given(shopifySyncer.supports(OrderChannel.SHOPIFY)).willReturn(true);
        given(shopifySyncer.syncOrders("seller-xyz"))
                .willReturn(new ChannelOrderSyncResponse(0, 0, List.of()));

        service.sync("seller-xyz", OrderChannel.SHOPIFY);

        then(shopifySyncer).should().syncOrders("seller-xyz");
    }
}

