package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderDto;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.ShopifyApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyOrderSyncService {

    private final ShopifyApiClient shopifyApiClient;
    private final ChannelOrderRepository channelOrderRepository;

    /**
     * Shopify API에서 주문 목록을 가져와 channel_order 테이블에 저장
     * - 이미 존재하는 orderId는 skip (멱등성 보장)
     *
     * @param sellerId 동기화할 sellerId
     */
    @Transactional
    public void syncOrders(String sellerId) {
        List<ShopifyOrderDto> orders = shopifyApiClient.getOrders();
        log.info("Shopify API에서 {}건 주문 조회 완료 (sellerId={})", orders.size(), sellerId);

        for (ShopifyOrderDto dto : orders) {
            String orderId = String.valueOf(dto.getId());

            if (channelOrderRepository.existsById(orderId)) {
                log.debug("중복 주문 skip: {}", orderId);
                continue;
            }

            ChannelOrder order = toChannelOrder(dto, sellerId);
            channelOrderRepository.save(order);
            log.debug("주문 저장 완료: {} ({})", orderId, dto.getName());
        }
    }

    private ChannelOrder toChannelOrder(ShopifyOrderDto dto, String sellerId) {
        ShopifyOrderDto.ShippingAddress addr = dto.getShippingAddress();

        return ChannelOrder.builder()
                .orderId(String.valueOf(dto.getId()))
                .channelOrderNo(dto.getName())
                .orderChannel(OrderChannel.SHOPIFY)
                .orderedAt(parseDateTime(dto.getCreatedAt()))
                .receiverName(addr != null ? addr.getName() : null)
                .receiverPhoneNo(addr != null ? addr.getPhone() : null)
                .shipToAddress1(addr != null ? addr.getAddress1() : null)
                .shipToAddress2(addr != null ? addr.getAddress2() : null)
                .shipToState(addr != null ? addr.getProvinceCode() : null)
                .shipToCity(addr != null ? addr.getCity() : null)
                .shipToZipCode(addr != null ? addr.getZip() : null)
                .sellerId(sellerId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return OffsetDateTime.parse(dateStr).toLocalDateTime();
    }
}
