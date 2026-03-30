package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.SellerChannelCardDto;
import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.repository.ChannelApiRepository;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerChannelCardQueryService {

    private final ChannelApiRepository channelApiRepository;
    private final ChannelOrderRepository channelOrderRepository;

    /**
     * INT-001 — 셀러 채널 연결 카드 목록 조회
     * 셀러가 등록한 각 채널에 대해 통계 정보를 계산하여 반환
     */
    public List<SellerChannelCardDto> getChannelCards(String sellerId) {
        validateSellerId(sellerId);

        List<ChannelApi> channels = channelApiRepository.findByIdSellerId(sellerId);
        List<ChannelOrder> allOrders = channelOrderRepository.findBySellerId(sellerId);

        // 채널별 주문 그룹핑
        Map<String, List<ChannelOrder>> ordersByChannel = allOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderChannel().name()));

        return channels.stream()
                .map(channel -> toCard(channel, ordersByChannel))
                .collect(Collectors.toList());
    }

    private SellerChannelCardDto toCard(ChannelApi channel, Map<String, List<ChannelOrder>> ordersByChannel) {
        String channelName = channel.getId().getChannelName();
        List<ChannelOrder> orders = ordersByChannel.getOrDefault(channelName, List.of());

        LocalDate today = LocalDate.now();

        int pendingOrders = (int) orders.stream()
                .filter(o -> o.getInvoiceNo() == null)
                .count();

        int todayImported = (int) orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().equals(today))
                .count();

        LocalDateTime lastSyncedAt = orders.stream()
                .map(ChannelOrder::getCreatedAt)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return SellerChannelCardDto.builder()
                .key(channelName)
                .label(toLabel(channelName))
                .syncStatus(orders.isEmpty() ? "PLANNED" : "ACTIVE")
                .pendingOrders(pendingOrders)
                .todayImported(todayImported)
                .lastSyncedAt(lastSyncedAt)
                .build();
    }

    String toLabel(String channelName) {
        if (channelName == null) return "";
        return switch (channelName.toUpperCase()) {
            case "SHOPIFY" -> "Shopify";
            case "AMAZON" -> "Amazon";
            case "MANUAL" -> "Manual";
            case "EXCEL"  -> "Excel";
            default       -> channelName;
        };
    }

    private void validateSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId는 필수입니다.");
        }
    }
}
