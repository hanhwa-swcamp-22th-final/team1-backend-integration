package com.conk.integration.query.service;

import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.SellerIdValidator;
import com.conk.integration.query.dto.SellerChannelOrderDto;
import com.conk.integration.query.dto.SellerChannelOrderQueryResult;
import com.conk.integration.query.dto.SellerOrderQueryParams;
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

    /**
     * 셀러의 주문 목록을 채널/검색어/페이지 조건으로 조회하고 표시용 필드를 계산해 반환한다.
     *
     * @param params 셀러ID, 채널, 검색어, 페이지 정보
     * @return 주문 목록 (status, itemsSummary 필드 포함)
     * @throws BusinessException         sellerId가 null이거나 공백인 경우 (INT-001)
     * @throws IllegalArgumentException  channel 값이 OrderChannel enum에 없는 경우
     */
    public List<SellerChannelOrderDto> getOrders(SellerOrderQueryParams params) {
        validate(params);
        // mapper 결과를 표시 전용 DTO로 일괄 변환한다.
        return channelOrderMapper.findBySellerIdWithItemSummary(params)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 셀러의 주문 목록 전체 개수를 동일한 필터 조건으로 조회한다.
     *
     * @param params 셀러ID, 채널, 검색어, 페이지 정보
     * @return 전체 주문 수
     */
    public int countOrders(SellerOrderQueryParams params) {
        validate(params);
        return channelOrderMapper.countBySellerIdWithFilters(params);
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
                .orderAmount(raw.getTotalAmount() != null ? raw.getTotalAmount().doubleValue() : 0D)
                .orderedAt(raw.getOrderedAt())
                .status(resolveStatus(raw.getInvoiceNo(), raw.getShippedAt()))
                .build();
    }

    // 첫 상품명과 품목 수를 리스트 화면용 한 줄 요약으로 만든다.
    String buildItemsSummary(String firstItemName, int itemCount) {
        if (firstItemName == null || firstItemName.isBlank()) return "";
        if (itemCount <= 1) return firstItemName;
        return firstItemName + ", ...";
    }

    // 송장/출고 시각 존재 여부로 주문 진행 상태를 계산한다.
    String resolveStatus(String invoiceNo, String shippedAt) {
        if (shippedAt != null && !shippedAt.isBlank()) return "SHIPPED";
        if (invoiceNo != null) return "PROCESSING";
        return "NEW";
    }

    private void validate(SellerOrderQueryParams params) {
        SellerIdValidator.requireValid(params.getSellerId());
        if (params.getChannel() != null && !params.getChannel().isBlank()) {
            OrderChannel.valueOf(params.getChannel());
        }
    }
}
