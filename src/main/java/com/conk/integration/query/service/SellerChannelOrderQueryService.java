package com.conk.integration.query.service;

import com.conk.integration.query.dto.SellerChannelOrderDto;
import com.conk.integration.query.dto.SellerChannelOrderQueryResult;
import com.conk.integration.query.mapper.SellerChannelOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerChannelOrderQueryService {

    private final SellerChannelOrderMapper channelOrderMapper;

    public List<SellerChannelOrderDto> getOrders(String sellerId) {
        validateSellerId(sellerId);
        return channelOrderMapper.findBySellerIdWithItemSummary(sellerId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private SellerChannelOrderDto toDto(SellerChannelOrderQueryResult raw) {
        return SellerChannelOrderDto.builder()
                .id(raw.getOrderId())
                .channel(raw.getOrderChannel())
                .channelOrderNo(raw.getChannelOrderNo())
                .conkOrderNo(raw.getOrderId())
                .recipient(raw.getReceiverName())
                .itemsSummary(buildItemsSummary(raw.getFirstItemName(), raw.getItemCount()))
                .orderAmount(null)
                .orderedAt(raw.getOrderedAt())
                .status(resolveStatus(raw.getInvoiceNo(), raw.getShippedAt()))
                .build();
    }

    String buildItemsSummary(String firstItemName, int itemCount) {
        if (firstItemName == null || firstItemName.isBlank()) return "";
        if (itemCount <= 1) return firstItemName;
        return firstItemName + " 외 " + (itemCount - 1) + "건";
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
