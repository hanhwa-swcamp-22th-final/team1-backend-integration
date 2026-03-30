package com.conk.integration.command.domain.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 주문 아이템은 복합 키와 기본 수량값이 올바르게 초기화되는지만 확인한다.
@DisplayName("ChannelOrderItem Entity Tests")
class ChannelOrderItemTest {

    // 주문 아이디와 SKU가 함께 묶여 아이템 정체성이 유지되는지 본다.
    @Test
    @DisplayName("빌더로 생성하면 복합키와 스냅샷 필드가 유지된다")
    void builder_setsCompositeKeyAndSnapshotFields() {
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORDER-ITEM-001")
                .sellerId("seller-001")
                .build();

        ChannelOrderItem item = ChannelOrderItem.builder()
                .id(new ChannelOrderItemId("ORDER-ITEM-001", "SKU-A1"))
                .channelOrder(order)
                .quantity(3)
                .productNameSnapshot("Test Product A")
                .build();

        assertThat(item.getId().getOrderId()).isEqualTo("ORDER-ITEM-001");
        assertThat(item.getId().getSkuId()).isEqualTo("SKU-A1");
        assertThat(item.getChannelOrder()).isSameAs(order);
        assertThat(item.getProductNameSnapshot()).isEqualTo("Test Product A");
        assertThat(item.getQuantity()).isEqualTo(3);
    }

    // picked/packed 수량은 작업 전 상태를 뜻하므로 기본값 0이 중요하다.
    @Test
    @DisplayName("생성 시 pickedQuantity와 packedQuantity의 기본값은 0이다")
    void builder_defaultsPickedAndPackedQuantityToZero() {
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORDER-ITEM-002")
                .sellerId("seller-001")
                .build();

        ChannelOrderItem item = ChannelOrderItem.builder()
                .id(new ChannelOrderItemId("ORDER-ITEM-002", "SKU-B1"))
                .channelOrder(order)
                .quantity(5)
                .build();

        assertThat(item.getPickedQuantity()).isZero();
        assertThat(item.getPackedQuantity()).isZero();
    }
}
