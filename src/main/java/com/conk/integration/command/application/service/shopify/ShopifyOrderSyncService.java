package com.conk.integration.command.application.service.shopify;

import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyOrderResponse;
import com.conk.integration.command.application.service.ChannelOrderIdGenerator;
import com.conk.integration.command.application.service.ChannelOrderSyncer;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.client.OrderServiceClient;
import com.conk.integration.command.infrastructure.client.WmsClient;
import com.conk.integration.command.infrastructure.client.dto.ShopifyOrderSyncRequest;
import com.conk.integration.command.infrastructure.repository.ChannelOrderItemRepository;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyOrderClient;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.query.service.ChannelApiQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// Shopify GraphQL 주문 응답을 내부 ChannelOrder/ChannelOrderItem 엔티티로 변환해 저장하고 order-service와 동기화한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyOrderSyncService implements ChannelOrderSyncer {

    private final ShopifyOrderClient shopifyOrderClient;
    private final ChannelOrderRepository channelOrderRepository;
    private final ChannelOrderItemRepository channelOrderItemRepository;
    private final ChannelApiQueryService channelApiQueryService;
    private final ChannelOrderIdGenerator channelOrderIdGenerator;
    private final OrderServiceClient orderServiceClient;
    private final WmsClient wmsClient;

    @Override
    public boolean supports(OrderChannel channel) {
        return channel == OrderChannel.SHOPIFY;
    }

    @Override
    @Transactional
    public ChannelOrderSyncResponse syncOrders(String sellerId) {
        ChannelCredential credential = channelApiQueryService.findChannelCredential(sellerId, OrderChannel.SHOPIFY.name());
        List<ShopifyOrderResponse.OrderNode> orders = shopifyOrderClient.getOrders(
                credential.getStoreName(),
                credential.getChannelApi());
        log.info("Shopify GraphQL API에서 {}건 주문 조회 완료 (sellerId={})", orders.size(), sellerId);

        int savedCount = 0;
        int skippedCount = 0;
        int failedSyncCount = 0;
        List<ChannelOrder> savedOrders = new ArrayList<>();

        for (ShopifyOrderResponse.OrderNode node : orders) {
            String channelOrderNo = node.getName();

            if (channelOrderRepository.existsBySellerIdAndChannelOrderNo(sellerId, channelOrderNo)) {
                log.debug("중복 주문 skip: {}", channelOrderNo);
                skippedCount++;
                continue;
            }

            if (!allSkusRegisteredInWms(node, channelOrderNo)) {
                log.warn("WMS 미등록 SKU가 포함된 주문 반려: channelOrderNo={}", channelOrderNo);
                skippedCount++;
                continue;
            }

            String orderId = channelOrderIdGenerator.generate(OrderChannel.SHOPIFY);

            ChannelOrder order = toChannelOrder(node, orderId, sellerId);
            channelOrderRepository.save(order);

            List<ChannelOrderItem> items = order.getItems();
            items.forEach(channelOrderItemRepository::save);
            log.debug("주문 저장 완료: orderId={}, channelOrderNo={}, amount={}, items={}건",
                    orderId, channelOrderNo, order.getTotalAmount(), items.size());

            savedOrders.add(order);
            savedCount++;

            try {
                orderServiceClient.syncToOrderService(sellerId, toSyncRequest(order));
                log.info("Order Service 동기화 성공: orderId={}", orderId);
            } catch (Exception e) {
                failedSyncCount++;
                log.warn("Order Service 동기화 실패 (계속 진행): orderId={}, error={}", orderId, e.getMessage());
            }
        }

        log.info("동기화 완료 — 저장: {}건, skip: {}건, 동기화실패: {}건 (sellerId={})",
                savedCount, skippedCount, failedSyncCount, sellerId);
        return new ChannelOrderSyncResponse(savedCount, skippedCount, failedSyncCount,
                savedOrders.stream().map(ChannelOrderSyncResponse.OrderDto::from).toList());
    }

    // Shopify GraphQL 주문 노드를 내부 저장용 엔티티로 정규화한다.
    private ChannelOrder toChannelOrder(ShopifyOrderResponse.OrderNode node,
                                        String orderId, String sellerId) {
        ShopifyOrderResponse.ShippingAddress addr = node.getShippingAddress();

        ChannelOrder order = ChannelOrder.builder()
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
                .totalAmount(parseAmount(node.getCurrentTotalPriceSet()))
                .build();

        buildItems(node, orderId, order);
        return order;
    }

    // lineItems를 ChannelOrderItem으로 변환해 order에 추가한다.
    private void buildItems(ShopifyOrderResponse.OrderNode node, String orderId, ChannelOrder order) {
        if (node.getLineItems() == null || node.getLineItems().getEdges() == null) {
            return;
        }

        for (ShopifyOrderResponse.LineItemEdge edge : node.getLineItems().getEdges()) {
            ShopifyOrderResponse.LineItemNode lineItem = edge.getNode();
            String skuId = resolveSkuId(lineItem);

            if (skuId == null) {
                log.warn("skuId를 결정할 수 없는 line item skip — orderId={}, title={}", orderId, lineItem.getTitle());
                continue;
            }

            ChannelOrderItem item = ChannelOrderItem.builder()
                    .id(new ChannelOrderItemId(orderId, skuId))
                    .channelOrder(order)
                    .quantity(lineItem.getQuantity())
                    .productNameSnapshot(lineItem.getTitle())
                    .build();
            order.addItem(item);
        }
    }

    // order-service 호출용 요청 DTO로 변환한다.
    private ShopifyOrderSyncRequest toSyncRequest(ChannelOrder order) {
        ShopifyOrderSyncRequest.ShippingAddress addr = new ShopifyOrderSyncRequest.ShippingAddress(
                order.getShipToAddress1(),
                order.getShipToAddress2(),
                order.getShipToCity(),
                order.getShipToState(),
                order.getShipToZipCode());

        List<ShopifyOrderSyncRequest.OrderItem> items = order.getItems().stream()
                .map(item -> new ShopifyOrderSyncRequest.OrderItem(
                        item.getId().getSkuId(),
                        item.getQuantity(),
                        item.getProductNameSnapshot()))
                .toList();

        return new ShopifyOrderSyncRequest(
                order.getOrderedAt(),
                order.getChannelOrderNo(),
                order.getReceiverName(),
                order.getReceiverPhoneNo(),
                null,
                addr,
                items);
    }

    // sku(not blank) → variant GID 끝 숫자 → null 순서로 skuId를 결정한다.
    private String resolveSkuId(ShopifyOrderResponse.LineItemNode lineItem) {
        if (lineItem.getSku() != null && !lineItem.getSku().isBlank()) {
            return lineItem.getSku();
        }
        if (lineItem.getVariant() != null && lineItem.getVariant().getId() != null) {
            return extractIdFromGid(lineItem.getVariant().getId());
        }
        return null;
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

    // Shopify의 가격 문자열("29.99")을 BigDecimal로 변환한다.
    private BigDecimal parseAmount(String price) {
        if (price == null || price.isBlank()) return null;
        try {
            return new BigDecimal(price);
        } catch (NumberFormatException e) {
            log.warn("주문금액 파싱 실패: '{}'", price);
            return null;
        }
    }

    // 주문의 모든 lineItem SKU가 WMS에 등록되어 있는지 확인한다. 하나라도 미등록이면 false를 반환한다.
    private boolean allSkusRegisteredInWms(ShopifyOrderResponse.OrderNode node, String channelOrderNo) {
        if (node.getLineItems() == null || node.getLineItems().getEdges() == null) {
            return true;
        }
        for (ShopifyOrderResponse.LineItemEdge edge : node.getLineItems().getEdges()) {
            ShopifyOrderResponse.LineItemNode lineItem = edge.getNode();
            String skuId = resolveSkuId(lineItem);
            if (skuId == null) {
                log.warn("SKU 확인 불가 line item 존재 — channelOrderNo={}, title={}", channelOrderNo, lineItem.getTitle());
                return false;
            }
            if (!wmsClient.skuExists(skuId)) {
                log.warn("WMS 미등록 SKU — channelOrderNo={}, skuId={}", channelOrderNo, skuId);
                return false;
            }
        }
        return true;
    }
}
