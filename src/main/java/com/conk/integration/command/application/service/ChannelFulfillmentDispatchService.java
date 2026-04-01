package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.BulkFulfillmentResponse;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.query.dto.FulfillmentTargetDto;
import com.conk.integration.query.mapper.ChannelFulfillmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

// 주문/송장 조회와 채널별 sender 선택을 담당하는 fulfillment orchestration 서비스다.
@Service
@RequiredArgsConstructor
public class ChannelFulfillmentDispatchService {

    private final ChannelOrderRepository channelOrderRepository;
    private final EasypostShipmentInvoiceRepository invoiceRepository;
    private final ChannelFulfillmentMapper channelFulfillmentMapper;
    private final List<ChannelFulfillmentSender> senders;

    /**
     * 단건 주문을 채널 fulfillment API로 전송한다.
     *
     * @param orderId 내부 주문 ID
     * @throws IllegalArgumentException 주문 또는 지원 sender가 없는 경우
     * @throws IllegalStateException    송장이 발급되지 않았거나 송장 엔티티가 없는 경우
     */
    public void fulfill(String orderId) {
        ChannelOrder order = channelOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("ChannelOrder를 찾을 수 없습니다: " + orderId));

        if (order.getInvoiceNo() == null) {
            throw new IllegalStateException("송장이 발급되지 않은 주문입니다: " + orderId);
        }

        EasypostShipmentInvoice invoice = invoiceRepository.findById(order.getInvoiceNo())
                .orElseThrow(() -> new IllegalStateException("EasypostShipmentInvoice를 찾을 수 없습니다: " + order.getInvoiceNo()));

        ChannelFulfillmentSender sender = senders.stream()
                .filter(candidate -> candidate.supports(order.getOrderChannel()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 fulfillment 채널입니다: " + order.getOrderChannel()));

        sender.send(order, invoice);
    }

    /**
     * channelSyncYn=false인 미전송 주문을 채널별로 일괄 전송하고, 성공한 주문을 synced 처리한다.
     *
     * @param sellerId     셀러 식별자
     * @param orderChannel 대상 채널
     * @return 성공/실패 건수 요약
     */
    @Transactional
    public BulkFulfillmentResponse fulfillBulk(String sellerId, OrderChannel orderChannel) {
        List<FulfillmentTargetDto> targets = channelFulfillmentMapper.findUnsyncedTargets(
                sellerId, orderChannel.name());

        if (targets.isEmpty()) {
            return new BulkFulfillmentResponse(0, 0);
        }

        ChannelFulfillmentSender sender = senders.stream()
                .filter(candidate -> candidate.supports(orderChannel))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 fulfillment 채널입니다: " + orderChannel));

        sender.sendBulk(sellerId, targets);

        List<String> orderIds = targets.stream()
                .map(FulfillmentTargetDto::getOrderId)
                .collect(Collectors.toList());
        channelOrderRepository.findAllById(orderIds).forEach(ChannelOrder::markAsSynced);

        return new BulkFulfillmentResponse(targets.size(), 0);
    }
}
