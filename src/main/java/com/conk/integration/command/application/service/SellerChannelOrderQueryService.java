package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.SellerChannelOrderDto;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerChannelOrderQueryService {

    private final ChannelOrderRepository channelOrderRepository;

    /**
     * INT-002 — 셀러 채널 통합 주문 목록 조회
     * items JOIN FETCH로 N+1 방지
     */
    public List<SellerChannelOrderDto> getOrders(String sellerId) {
        validateSellerId(sellerId);
        return channelOrderRepository.findBySellerIdWithItems(sellerId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private SellerChannelOrderDto toDto(ChannelOrder order) {
        return SellerChannelOrderDto.builder()
                .id(order.getOrderId())
                .channel(order.getOrderChannel() != null ? order.getOrderChannel().name() : null)
                .channelOrderNo(order.getChannelOrderNo())
                .conkOrderNo(order.getOrderId())
                .recipient(order.getReceiverName())
                .itemsSummary(buildItemsSummary(order.getItems()))
                .orderAmount(null)   // TODO: ORDER 서비스 연동 후 product.sale_price_amt 기반 계산
                .orderedAt(order.getOrderedAt())
                .status(resolveStatus(order.getInvoiceNo(), order.getShippedAt()))
                .build();
    }

    String buildItemsSummary(List<ChannelOrderItem> items) {
        if (items == null || items.isEmpty()) return "";
        String firstName = items.get(0).getProductNameSnapshot();
        String name = firstName != null ? firstName : "";
        if (items.size() == 1) return name;
        return name + " 외 " + (items.size() - 1) + "건";
    }

    String resolveStatus(String invoiceNo, String shippedAt) {
        if (shippedAt != null && !shippedAt.isBlank()) return "SHIPPED";
        if (invoiceNo != null) return "PROCESSING";
        return "NEW";
    }

    private void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId는 필수입니다.");
        }
    }
}
