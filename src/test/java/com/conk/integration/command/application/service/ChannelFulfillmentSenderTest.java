package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChannelFulfillmentSender 테스트")
class ChannelFulfillmentSenderTest {

    private final ChannelFulfillmentSender sender = new ChannelFulfillmentSender() {
        @Override
        public boolean supports(OrderChannel channel) {
            return false;
        }

        @Override
        public void send(ChannelOrder order, EasypostShipmentInvoice invoice) {
            // no-op
        }
    };

    @Test
    @DisplayName("기본 구현으로 일괄 fulfillment를 수행하면 BusinessException(INT-003)을 발생시켜야 한다")
    void sendBulk_defaultImplementation_throwsUnsupportedBulkFulfillment() {
        assertThatThrownBy(() -> sender.sendBulk("seller-001", List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_BULK_FULFILLMENT);
    }
}

