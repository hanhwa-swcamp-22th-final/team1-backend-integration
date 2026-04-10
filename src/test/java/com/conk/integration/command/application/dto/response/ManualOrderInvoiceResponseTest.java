package com.conk.integration.command.application.dto.response;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ManualOrderInvoiceResponse 테스트")
class ManualOrderInvoiceResponseTest {

    @Test
    @DisplayName("주문과 송장 정보가 주어지면 응답 객체로 변환했을 때 주요 필드를 정확히 매핑해야 한다")
    void of_mapsOrderAndInvoiceFieldsCorrectly() {
        ChannelOrder order = buildOrder();
        order.addItem(buildItem(order, "SKU-001", "상품 A", 2));
        EasypostShipmentInvoice invoice = buildInvoice();

        ManualOrderInvoiceResponse response = ManualOrderInvoiceResponse.of(order, invoice);

        assertThat(response.getOrderId()).isEqualTo("ORD-001");
        assertThat(response.getReceiverName()).isEqualTo("홍길동");
        assertThat(response.getInvoiceNo()).isEqualTo("INV-001");
        assertThat(response.getTrackingCode()).isEqualTo("TRK-001");
        assertThat(response.getCarrierType()).isEqualTo("USPS");
        assertThat(response.getFreightChargeAmt()).isEqualTo(550);
        assertThat(response.getTrackingUrl()).isEqualTo("https://track.easypost.com/TRK-001");
        assertThat(response.getLabelFileUrl()).isEqualTo("https://label.url/INV-001.pdf");
    }

    @Test
    @DisplayName("빈 주소 조각이 포함된 주문 정보가 주어지면 응답 객체로 변환했을 때 배송지 주소를 조합해 반환해야 한다")
    void of_buildsShipToAddressWithoutBlankParts() {
        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORD-001")
                .orderChannel(OrderChannel.MANUAL)
                .sellerId("seller-001")
                .receiverName("홍길동")
                .shipToAddress1("123 Main St")
                .shipToAddress2("")
                .shipToCity("Los Angeles")
                .shipToState("CA")
                .shipToZipCode("90001")
                .build();
        EasypostShipmentInvoice invoice = buildInvoice();

        ManualOrderInvoiceResponse response = ManualOrderInvoiceResponse.of(order, invoice);

        assertThat(response.getShipToAddress()).isEqualTo("123 Main St, Los Angeles, CA, 90001");
    }

    @Test
    @DisplayName("주문 아이템이 포함된 주문 정보가 주어지면 응답 객체로 변환했을 때 주문 아이템 목록으로 변환해야 한다")
    void of_mapsItemsToOrderItemBodies() {
        ChannelOrder order = buildOrder();
        order.addItem(buildItem(order, "SKU-001", "상품 A", 2));
        order.addItem(buildItem(order, "SKU-002", "상품 B", 1));

        ManualOrderInvoiceResponse response = ManualOrderInvoiceResponse.of(order, buildInvoice());

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems())
                .extracting(ManualOrderInvoiceResponse.OrderItemBody::getSkuId)
                .containsExactly("SKU-001", "SKU-002");
    }

    private ChannelOrder buildOrder() {
        return ChannelOrder.builder()
                .orderId("ORD-001")
                .orderChannel(OrderChannel.MANUAL)
                .sellerId("seller-001")
                .receiverName("홍길동")
                .shipToAddress1("123 Main St")
                .shipToState("CA")
                .shipToCity("Los Angeles")
                .shipToZipCode("90001")
                .build();
    }

    private ChannelOrderItem buildItem(ChannelOrder order, String skuId, String productName, int quantity) {
        return ChannelOrderItem.builder()
                .id(new ChannelOrderItemId(order.getOrderId(), skuId))
                .channelOrder(order)
                .productNameSnapshot(productName)
                .quantity(quantity)
                .build();
    }

    private EasypostShipmentInvoice buildInvoice() {
        return EasypostShipmentInvoice.builder()
                .invoiceNo("INV-001")
                .trackingCode("TRK-001")
                .carrierType(CarrierType.USPS)
                .freightChargeAmt(550)
                .trackingUrl("https://track.easypost.com/TRK-001")
                .labelFileUrl("https://label.url/INV-001.pdf")
                .build();
    }
}
