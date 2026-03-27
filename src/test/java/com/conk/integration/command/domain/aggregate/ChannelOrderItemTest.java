package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("ChannelOrderItem Entity Tests")
class ChannelOrderItemTest {

    @Autowired
    private ChannelOrderRepository orderRepository;

    private ChannelOrder saveOrder(String orderId) {
        return orderRepository.save(
                ChannelOrder.builder()
                        .orderId(orderId)
                        .sellerId("seller-001")
                        .build()
        );
    }

    @Test
    @DisplayName("주문 아이템 복합키 저장")
    void save_OrderItem_WithCompositeKey() {
        // given
        ChannelOrder order = saveOrder("ORDER-ITEM-001");
        ChannelOrderItem item = ChannelOrderItem.builder()
                .id(new ChannelOrderItemId("ORDER-ITEM-001", "SKU-A1"))
                .channelOrder(order)
                .quantity(3)
                .productNameSnapshot("Test Product A")
                .pickedQuantity(0)
                .packedQuantity(0)
                .build();
        order.addItem(item);

        // when
        ChannelOrder saved = orderRepository.save(order);

        // then
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getId().getSkuId()).isEqualTo("SKU-A1");
        assertThat(saved.getItems().getFirst().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("cascade로 아이템 자동 저장")
    void cascade_SaveItems_WithOrder() {
        // given
        ChannelOrder order = saveOrder("ORDER-ITEM-002");
        order.addItem(ChannelOrderItem.builder()
                .id(new ChannelOrderItemId("ORDER-ITEM-002", "SKU-B1"))
                .channelOrder(order)
                .quantity(5)
                .productNameSnapshot("Product B")
                .pickedQuantity(0)
                .packedQuantity(0)
                .build());
        order.addItem(ChannelOrderItem.builder()
                .id(new ChannelOrderItemId("ORDER-ITEM-002", "SKU-B2"))
                .channelOrder(order)
                .quantity(2)
                .productNameSnapshot("Product B2")
                .pickedQuantity(0)
                .packedQuantity(0)
                .build());

        // when — order.save() 만으로 items 자동 저장 (CascadeType.ALL)
        ChannelOrder saved = orderRepository.save(order);

        // then
        assertThat(saved.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("orphanRemoval로 아이템 삭제")
    void orphanRemoval_DeleteItem() {
        // given
        ChannelOrder order = saveOrder("ORDER-ITEM-003");
        ChannelOrderItem item = ChannelOrderItem.builder()
                .id(new ChannelOrderItemId("ORDER-ITEM-003", "SKU-C1"))
                .channelOrder(order)
                .quantity(1)
                .productNameSnapshot("Product C")
                .pickedQuantity(0)
                .packedQuantity(0)
                .build();
        order.addItem(item);
        orderRepository.save(order);

        // when — items 리스트에서 제거 후 save → orphanRemoval로 DB에서 삭제
        order.getItems().clear();
        orderRepository.save(order);

        // then
        ChannelOrder found = orderRepository.findById("ORDER-ITEM-003").get();
        assertThat(found.getItems()).isEmpty();
    }
}
