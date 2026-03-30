package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// 주문/송장 조회와 채널별 sender 선택을 담당하는 fulfillment orchestration 서비스다.
@Service
@RequiredArgsConstructor
public class ChannelFulfillmentDispatchService {

    private final ChannelOrderRepository channelOrderRepository;
    private final EasypostShipmentInvoiceRepository invoiceRepository;
    private final List<ChannelFulfillmentSender> senders;

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
}
