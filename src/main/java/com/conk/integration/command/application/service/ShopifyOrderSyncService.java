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
import java.util.function.Function;

// Shopify 주문 응답을 내부 ChannelOrder 엔티티로 변환해 저장한다.
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

            // 같은 orderId가 이미 있으면 재저장하지 않아 멱등성을 유지한다.
            if (channelOrderRepository.existsById(orderId)) {
                log.debug("중복 주문 skip: {}", orderId);
                continue;
            }

            channelOrderRepository.save(toChannelOrder(dto, sellerId));
            log.debug("주문 저장 완료: {} ({})", orderId, dto.getName());
        }
    }

    // Shopify 주문 DTO를 내부 저장용 엔티티로 정규화한다.
    private ChannelOrder toChannelOrder(ShopifyOrderDto dto, String sellerId) {
        ShopifyOrderDto.ShippingAddress addr = dto.getShippingAddress();

        return ChannelOrder.builder()
                .orderId(String.valueOf(dto.getId()))
                .channelOrderNo(dto.getName())
                .orderChannel(OrderChannel.SHOPIFY)
                .orderedAt(parseDateTime(dto.getCreatedAt()))
                .receiverName(addrField(addr, ShopifyOrderDto.ShippingAddress::getName))
                .receiverPhoneNo(addrField(addr, ShopifyOrderDto.ShippingAddress::getPhone))
                .shipToAddress1(addrField(addr, ShopifyOrderDto.ShippingAddress::getAddress1))
                .shipToAddress2(addrField(addr, ShopifyOrderDto.ShippingAddress::getAddress2))
                .shipToState(addrField(addr, ShopifyOrderDto.ShippingAddress::getProvinceCode))
                .shipToCity(addrField(addr, ShopifyOrderDto.ShippingAddress::getCity))
                .shipToZipCode(addrField(addr, ShopifyOrderDto.ShippingAddress::getZip))
                .sellerId(sellerId)
                .build();
    }

    // shippingAddress가 비어 있는 주문도 null 안전하게 처리한다.
    private <T> T addrField(ShopifyOrderDto.ShippingAddress addr,
                             Function<ShopifyOrderDto.ShippingAddress, T> getter) {
        return addr != null ? getter.apply(addr) : null;
    }

    // Shopify의 ISO-8601 시각 문자열을 내부 LocalDateTime으로 변환한다.
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return OffsetDateTime.parse(dateStr).toLocalDateTime();
    }
}
