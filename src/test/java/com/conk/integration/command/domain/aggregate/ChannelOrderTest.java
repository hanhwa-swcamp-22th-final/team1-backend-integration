package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// 1. 테스트 코드 (Red -> Green)
@DataJpaTest
@DisplayName("ChannelOrder Entity Tests")
public class ChannelOrderTest {

    @Autowired
    private ChannelOrderRepository repository;

    @Test
    @DisplayName("주문 등록")
    void register_Order() {
        // given (준비)
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORDER-001")
                .channelOrderNo("CH-ORDER-001")
                .orderChannel(OrderChannel.AMAZON)
                .orderedAt(LocalDateTime.now())
                .receiverName("홍길동")
                .receiverPhoneNo("010-1234-5678")
                .shipToAddress1("123 Main St")
                .shipToCity("Los Angeles")
                .shipToState("CA")
                .shipToZipCode("90001")
                .sellerId("seller-001")
                .build();

        // when (실행)
        ChannelOrder saved = repository.save(order);

        // then (검증)
        assertThat(saved.getOrderId()).isEqualTo("ORDER-001");
        assertThat(saved.getReceiverName()).isEqualTo("정현호");
        assertThat(saved.getOrderChannel()).isEqualTo(OrderChannel.AMAZON);
    }

    @Test
    @DisplayName("sellerId로 주문 목록 조회")
    void findBySellerId() {
        // given
        ChannelOrder order1 = ChannelOrder.builder()
                .orderId("ORDER-002")
                .sellerId("seller-001")
                .build();
        ChannelOrder order2 = ChannelOrder.builder()
                .orderId("ORDER-003")
                .sellerId("seller-001")
                .build();
        repository.save(order1);
        repository.save(order2);

        // when
        List<ChannelOrder> orders = repository.findBySellerId("seller-001");

        // then
        assertThat(orders).hasSize(2);
    }

    @Test
    @DisplayName("invoice_no 문자열 참조 저장")
    void assign_InvoiceNo() {
        // given
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORDER-004")
                .sellerId("seller-001")
                .invoiceNo("INV-001")
                .build();

        // when
        ChannelOrder saved = repository.save(order);

        // then
        assertThat(saved.getInvoiceNo()).isEqualTo("INV-001");

        Optional<ChannelOrder> found = repository.findById("ORDER-004");
        assertThat(found).isPresent();
        assertThat(found.get().getInvoiceNo()).isEqualTo("INV-001");
    }
}
