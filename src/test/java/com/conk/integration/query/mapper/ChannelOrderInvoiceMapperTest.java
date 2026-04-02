package com.conk.integration.query.mapper;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.query.dto.InvoiceTargetDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ChannelOrderInvoiceMapper가 invoiceNo=null인 주문만 올바르게 필터링하는지 DB 연동으로 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ChannelOrderInvoiceMapper Tests")
class ChannelOrderInvoiceMapperTest {

    @Autowired
    private ChannelOrderInvoiceMapper channelOrderInvoiceMapper;

    @Autowired
    private ChannelOrderRepository channelOrderRepository;

    @Test
    @DisplayName("findOrdersWithoutInvoice — invoiceNo가 null인 주문만 반환된다")
    void findOrdersWithoutInvoice_returnsOnlyNullInvoiceOrders() {
        channelOrderRepository.saveAndFlush(orderWithoutInvoice("ORD-NO-INV-001", "seller-A", "Alice"));
        channelOrderRepository.saveAndFlush(orderWithoutInvoice("ORD-NO-INV-002", "seller-A", "Bob"));
        channelOrderRepository.saveAndFlush(orderWithInvoice("ORD-HAS-INV-001", "seller-A", "INV-001"));

        List<InvoiceTargetDto> result = channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-A");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(InvoiceTargetDto::getOrderId)
                .containsExactlyInAnyOrder("ORD-NO-INV-001", "ORD-NO-INV-002");
    }

    @Test
    @DisplayName("findOrdersWithoutInvoice — 다른 sellerId의 주문은 반환되지 않는다")
    void findOrdersWithoutInvoice_doesNotReturnOtherSellerOrders() {
        channelOrderRepository.saveAndFlush(orderWithoutInvoice("ORD-SELLER-A-001", "seller-A", "Alice"));
        channelOrderRepository.saveAndFlush(orderWithoutInvoice("ORD-SELLER-B-001", "seller-B", "Bob"));

        List<InvoiceTargetDto> result = channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-A");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo("ORD-SELLER-A-001");
    }

    @Test
    @DisplayName("findOrdersWithoutInvoice — 조회 대상이 없으면 빈 리스트를 반환한다")
    void findOrdersWithoutInvoice_returnsEmptyList() {
        List<InvoiceTargetDto> result = channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-EMPTY");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findOrdersWithoutInvoice — receiverName, shipToAddress1 필드가 올바르게 매핑된다")
    void findOrdersWithoutInvoice_mapsFieldsCorrectly() {
        channelOrderRepository.saveAndFlush(ChannelOrder.builder()
                .orderId("ORD-FIELD-001")
                .channelOrderNo("#FIELD-001")
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId("seller-C")
                .receiverName("Charlie")
                .receiverPhoneNo("1234567890")
                .shipToAddress1("100 Main St")
                .shipToCity("New York")
                .shipToState("NY")
                .shipToZipCode("10001")
                .build());

        List<InvoiceTargetDto> result = channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-C");

        assertThat(result).hasSize(1);
        InvoiceTargetDto dto = result.get(0);
        assertThat(dto.getOrderId()).isEqualTo("ORD-FIELD-001");
        assertThat(dto.getReceiverName()).isEqualTo("Charlie");
        assertThat(dto.getShipToAddress1()).isEqualTo("100 Main St");
        assertThat(dto.getShipToCity()).isEqualTo("New York");
        assertThat(dto.getShipToState()).isEqualTo("NY");
        assertThat(dto.getShipToZipCode()).isEqualTo("10001");
    }

    private ChannelOrder orderWithoutInvoice(String orderId, String sellerId, String receiverName) {
        return ChannelOrder.builder()
                .orderId(orderId)
                .channelOrderNo("#" + orderId)
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId(sellerId)
                .receiverName(receiverName)
                .build();
    }

    private ChannelOrder orderWithInvoice(String orderId, String sellerId, String invoiceNo) {
        return ChannelOrder.builder()
                .orderId(orderId)
                .channelOrderNo("#" + orderId)
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId(sellerId)
                .invoiceNo(invoiceNo)
                .build();
    }
}