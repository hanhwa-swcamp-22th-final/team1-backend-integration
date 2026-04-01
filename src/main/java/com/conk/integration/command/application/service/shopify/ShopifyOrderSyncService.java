package com.conk.integration.command.application.service.shopify;

import com.conk.integration.command.application.dto.response.ShopifyOrderResponse;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.ShopifyOrderClient;
import com.conk.integration.query.dto.ShopifyCredentialDto;
import com.conk.integration.query.service.ChannelApiQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

// Shopify GraphQL 주문 응답을 내부 ChannelOrder 엔티티로 변환해 저장한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyOrderSyncService {

    private final ShopifyOrderClient shopifyOrderClient;
    private final ChannelOrderRepository channelOrderRepository;
    private final ChannelApiQueryService channelApiQueryService;

    /**
     * Shopify GraphQL API에서 주문 목록을 가져와 channel_order 테이블에 저장
     * - 이미 존재하는 orderId는 skip (멱등성 보장)
     * - fulfillmentOrderId를 함께 저장해 bulk fulfillment 전송에 활용한다.
     *
     * @param sellerId 동기화할 sellerId
     */
    @Transactional
    public void syncOrders(String sellerId) {
        ShopifyCredentialDto cred = channelApiQueryService.findShopifyCredential(sellerId);
        List<ShopifyOrderResponse.OrderNode> orders = shopifyOrderClient.getOrders(cred.getStoreName(), cred.getAccessToken());
        log.info("Shopify GraphQL API에서 {}건 주문 조회 완료 (sellerId={})", orders.size(), sellerId);

        for (ShopifyOrderResponse.OrderNode node : orders) {
            // GID에서 숫자 ID 추출: "gid://shopify/Order/12345" → "12345"
            String orderId = extractIdFromGid(node.getId());

            if (channelOrderRepository.existsById(orderId)) {
                log.debug("중복 주문 skip: {}", orderId);
                continue;
            }

            channelOrderRepository.save(toChannelOrder(node, orderId, sellerId));
            log.debug("주문 저장 완료: {} ({})", orderId, node.getName());
        }
    }

    // Shopify GraphQL 주문 노드를 내부 저장용 엔티티로 정규화한다.
    private ChannelOrder toChannelOrder(ShopifyOrderResponse.OrderNode node,
                                        String orderId, String sellerId) {
        ShopifyOrderResponse.ShippingAddress addr = node.getShippingAddress();

        return ChannelOrder.builder()
                .orderId(orderId)
                .channelOrderNo(node.getName())
                .orderChannel(OrderChannel.SHOPIFY)
                .orderedAt(parseDateTime(node.getCreatedAt()))
                .receiverName(addrField(addr, ShopifyOrderResponse.ShippingAddress::getName))
                .receiverPhoneNo(addrField(addr, ShopifyOrderResponse.ShippingAddress::getPhone))
                .shipToAddress1(addrField(addr, ShopifyOrderResponse.ShippingAddress::getAddress1))
                .shipToAddress2(addrField(addr, ShopifyOrderResponse.ShippingAddress::getAddress2))
                .shipToState(addrField(addr, ShopifyOrderResponse.ShippingAddress::getProvinceCode))
                .shipToCity(addrField(addr, ShopifyOrderResponse.ShippingAddress::getCity))
                .shipToZipCode(addrField(addr, ShopifyOrderResponse.ShippingAddress::getZip))
                .sellerId(sellerId)
                .fulfillmentOrderId(extractFulfillmentOrderId(node))
                .build();
    }

    // fulfillmentOrders 첫 번째 항목의 GID를 추출한다. 없으면 null을 반환한다.
    private String extractFulfillmentOrderId(ShopifyOrderResponse.OrderNode node) {
        if (node.getFulfillmentOrders() == null
                || node.getFulfillmentOrders().getEdges() == null
                || node.getFulfillmentOrders().getEdges().isEmpty()) {
            return null;
        }
        return node.getFulfillmentOrders().getEdges().get(0).getNode().getId();
    }

    // "gid://shopify/Order/12345" 형식에서 숫자 ID 부분만 추출한다.
    private String extractIdFromGid(String gid) {
        return gid.substring(gid.lastIndexOf('/') + 1);
    }

    // shippingAddress가 비어 있는 주문도 null 안전하게 처리한다.
    private <T> T addrField(ShopifyOrderResponse.ShippingAddress addr,
                             Function<ShopifyOrderResponse.ShippingAddress, T> getter) {
        return addr != null ? getter.apply(addr) : null;
    }

    // Shopify의 ISO-8601 시각 문자열을 내부 LocalDateTime으로 변환한다.
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return OffsetDateTime.parse(dateStr).toLocalDateTime();
    }
}
