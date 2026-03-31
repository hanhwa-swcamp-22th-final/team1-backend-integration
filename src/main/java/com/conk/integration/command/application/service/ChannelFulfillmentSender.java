package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.query.dto.FulfillmentTargetDto;

import java.util.List;

// 채널별 fulfillment 전송 전략이 따라야 하는 최소 계약이다.
public interface ChannelFulfillmentSender {

    boolean supports(OrderChannel channel);

    void send(ChannelOrder order, EasypostShipmentInvoice invoice);

    // bulk 전송을 지원하는 채널은 반드시 재정의해야 한다.
    default void sendBulk(String sellerId, List<FulfillmentTargetDto> targets) {
        throw new UnsupportedOperationException("sendBulk is not supported for this channel");
    }
}
