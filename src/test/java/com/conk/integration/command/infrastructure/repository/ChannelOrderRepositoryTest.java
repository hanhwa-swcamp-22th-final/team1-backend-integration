package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ChannelOrderRepository의 커스텀 조회와 JPA 매핑 동작을 분리해서 검증한다.
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ChannelOrderRepository Tests")
class ChannelOrderRepositoryTest {

    @Autowired
    private ChannelOrderRepository channelOrderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // sellerId 필터가 다른 셀러 주문을 섞어 반환하지 않는지 본다.
    @Test
    @DisplayName("findBySellerId()는 해당 sellerId의 주문만 반환한다")
    void findBySellerId_returnsOnlyMatchingSeller() {
        ChannelOrder orderA1 = baseOrder("O-A-001", "seller-A");
        ChannelOrder orderA2 = baseOrder("O-A-002", "seller-A");
        ChannelOrder orderB = baseOrder("O-B-001", "seller-B");

        channelOrderRepository.saveAll(List.of(orderA1, orderA2, orderB));

        List<ChannelOrder> result = channelOrderRepository.findBySellerId("seller-A");

        assertThat(result).hasSize(2)
                .extracting(ChannelOrder::getSellerId)
                .containsOnly("seller-A");
    }

    // existsById는 sync 로직의 중복 방지 분기에서 직접 사용된다.
    @Test
    @DisplayName("existsById()는 저장된 주문 ID에 대해서만 true를 반환한다")
    void existsById_returnsExpectedBoolean() {
        channelOrderRepository.save(baseOrder("O-EXISTS-001", "seller-X"));

        assertThat(channelOrderRepository.existsById("O-EXISTS-001")).isTrue();
        assertThat(channelOrderRepository.existsById("O-NOT-EXIST")).isFalse();
    }

    // 연관 아이템은 부모 주문 저장만으로 함께 영속화되어야 한다.
    @Test
    @DisplayName("주문 저장 시 연관 아이템이 cascade로 함께 저장된다")
    void save_cascadesItems() {
        ChannelOrder order = baseOrder("ORDER-ITEM-001", "seller-001");
        order.addItem(buildItem(order, "SKU-A1", 3, "Product A"));
        order.addItem(buildItem(order, "SKU-A2", 1, "Product B"));

        channelOrderRepository.saveAndFlush(order);
        flushAndClear();

        ChannelOrder found = channelOrderRepository.findById("ORDER-ITEM-001").orElseThrow();

        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getItems())
                .extracting(item -> item.getId().getSkuId())
                .containsExactlyInAnyOrder("SKU-A1", "SKU-A2");
    }

    // 컬렉션에서 제거된 자식이 실제 DB에서도 삭제되는지 orphanRemoval을 검증한다.
    @Test
    @DisplayName("아이템을 컬렉션에서 제거하고 저장하면 orphanRemoval로 삭제된다")
    void save_removesOrphanedItems() {
        ChannelOrder order = baseOrder("ORDER-ITEM-002", "seller-001");
        order.addItem(buildItem(order, "SKU-C1", 1, "Product C"));

        channelOrderRepository.saveAndFlush(order);
        flushAndClear();

        ChannelOrder found = channelOrderRepository.findById("ORDER-ITEM-002").orElseThrow();
        found.getItems().clear();

        channelOrderRepository.saveAndFlush(found);
        flushAndClear();

        ChannelOrder reloaded = channelOrderRepository.findById("ORDER-ITEM-002").orElseThrow();

        assertThat(reloaded.getItems()).isEmpty();
    }

    // 저장/조회 테스트에 공통으로 쓰는 최소 주문 fixture다.
    private ChannelOrder baseOrder(String orderId, String sellerId) {
        return ChannelOrder.builder()
                .orderId(orderId)
                .channelOrderNo("#" + orderId)
                .orderChannel(OrderChannel.SHOPIFY)
                .orderedAt(LocalDateTime.of(2026, 3, 30, 9, 0))
                .sellerId(sellerId)
                .build();
    }

    // SKU별 아이템 fixture를 짧게 재사용하기 위한 헬퍼다.
    private ChannelOrderItem buildItem(ChannelOrder order, String skuId, int quantity, String productName) {
        return ChannelOrderItem.builder()
                .id(new ChannelOrderItemId(order.getOrderId(), skuId))
                .channelOrder(order)
                .quantity(quantity)
                .productNameSnapshot(productName)
                .build();
    }

    // 영속성 컨텍스트 영향을 제거하고 실제 DB 상태로 다시 읽기 위해 사용한다.
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
