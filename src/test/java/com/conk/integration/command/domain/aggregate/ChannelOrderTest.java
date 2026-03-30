package com.conk.integration.command.domain.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelOrder Entity Tests")
class ChannelOrderTest {

    @Test
    @DisplayName("빌더로 생성하면 배송 정보와 송장 참조가 유지된다")
    void builder_setsShippingAndInvoiceFields() {
        LocalDateTime orderedAt = LocalDateTime.of(2026, 3, 30, 10, 15);

        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORDER-001")
                .channelOrderNo("CH-ORDER-001")
                .orderChannel(OrderChannel.SHOPIFY)
                .orderedAt(orderedAt)
                .receiverName("홍길동")
                .receiverPhoneNo("010-1234-5678")
                .shipToAddress1("123 Main St")
                .shipToAddress2("Apt 101")
                .shipToCity("Los Angeles")
                .shipToState("CA")
                .shipToZipCode("90001")
                .shippedAt("2026-03-30T11:00:00Z")
                .sellerId("seller-001")
                .invoiceNo("INV-001")
                .build();

        assertThat(order.getOrderId()).isEqualTo("ORDER-001");
        assertThat(order.getOrderedAt()).isEqualTo(orderedAt);
        assertThat(order.getShipToAddress2()).isEqualTo("Apt 101");
        assertThat(order.getShippedAt()).isEqualTo("2026-03-30T11:00:00Z");
        assertThat(order.getInvoiceNo()).isEqualTo("INV-001");
    }

    @Test
    @DisplayName("addItem()을 호출하면 주문 아이템 컬렉션에 항목이 추가된다")
    void addItem_addsItemToOrder() {
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORDER-002")
                .sellerId("seller-002")
                .build();

        ChannelOrderItem item = ChannelOrderItem.builder()
                .id(new ChannelOrderItemId("ORDER-002", "SKU-001"))
                .channelOrder(order)
                .quantity(2)
                .productNameSnapshot("Test Product")
                .build();

        order.addItem(item);

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().getFirst().getId().getSkuId()).isEqualTo("SKU-001");
        assertThat(order.getItems().getFirst().getQuantity()).isEqualTo(2);
    }
}
