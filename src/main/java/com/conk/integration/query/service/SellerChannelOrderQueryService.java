package com.conk.integration.query.service;

import com.conk.integration.query.dto.SellerChannelOrderDto;
import com.conk.integration.query.dto.SellerChannelOrderQueryResult;
import com.conk.integration.query.mapper.SellerChannelOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

// 주문 raw 결과를 API 응답용 DTO로 변환하고 상태/요약 문자열을 계산한다.
@Service
@RequiredArgsConstructor
public class SellerChannelOrderQueryService {

    private final SellerChannelOrderMapper channelOrderMapper;

    public List<SellerChannelOrderDto> getOrders(String sellerId) {
        validateSellerId(sellerId);
        // mapper 결과를 표시 전용 DTO로 일괄 변환한다.
        return channelOrderMapper.findBySellerIdWithItemSummary(sellerId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // 조회 raw 결과를 응답 계약에 맞는 필드명/표현으로 정규화한다.
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

    // 첫 상품명과 품목 수를 리스트 화면용 한 줄 요약으로 만든다.
    String buildItemsSummary(String firstItemName, int itemCount) {
        if (firstItemName == null || firstItemName.isBlank()) return "";
        if (itemCount <= 1) return firstItemName;
        return firstItemName + " 외 " + (itemCount - 1) + "건";
    }

    // 송장/출고 시각 존재 여부로 주문 진행 상태를 계산한다.
    String resolveStatus(String invoiceNo, String shippedAt) {
        if (shippedAt != null && !shippedAt.isBlank()) return "SHIPPED";
        if (invoiceNo != null) return "PROCESSING";
        return "NEW";
    }

    // 조회 API 공통 입력 검증.
    private void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId는 필수입니다.");
        }
    }
}
