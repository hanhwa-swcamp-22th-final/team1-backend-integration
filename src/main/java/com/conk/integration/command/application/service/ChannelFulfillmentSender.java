package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.query.dto.FulfillmentTargetDto;

import java.util.List;

// 채널별 fulfillment 전송 전략이 따라야 하는 최소 계약이다.
public interface ChannelFulfillmentSender {

    /**
     * 이 sender가 주어진 채널을 지원하는지 확인한다.
     *
     * @param channel 확인할 주문 채널
     * @return 지원하면 true
     */
    boolean supports(OrderChannel channel);

    /**
     * 단건 주문의 송장 정보를 채널 fulfillment API로 전송한다.
     *
     * @param order   전송할 채널 주문
     * @param invoice 첨부할 송장 엔티티
     */
    void send(ChannelOrder order, EasypostShipmentInvoice invoice);

    /**
     * 셀러의 미전송 주문 목록을 채널 fulfillment API로 일괄 전송한다.
     * bulk 전송을 지원하는 채널은 반드시 재정의해야 한다.
     *
     * @param sellerId 셀러 식별자
     * @param targets  전송 대상 DTO 목록
     * @throws UnsupportedOperationException 채널이 bulk 전송을 지원하지 않는 경우
     */
    default void sendBulk(String sellerId, List<FulfillmentTargetDto> targets) {
        throw new UnsupportedOperationException("sendBulk is not supported for this channel");
    }
}
