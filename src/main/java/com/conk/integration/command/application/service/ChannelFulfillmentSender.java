package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.OrderChannel;

// 채널별 fulfillment 전송 전략이 따라야 하는 최소 계약이다.
public interface ChannelFulfillmentSender {

    boolean supports(OrderChannel channel);

    void send(ChannelOrder order, EasypostShipmentInvoice invoice);
}
