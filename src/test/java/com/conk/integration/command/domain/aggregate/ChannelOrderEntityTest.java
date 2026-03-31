package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.aggregate.*;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * [1단계] Domain / Entity 단위 테스트
 *
 * - 순수 Java 객체 수준에서 도메인 모델의 생성 / 상태 / 비즈니스 규칙을 검증합니다.
 * - Spring Context 없이 실행되므로 매우 빠릅니다.
 */
@DisplayName("[Entity] 도메인 엔티티 단위 테스트")
class ChannelOrderEntityTest {

    /* ===================================================================
     * ChannelOrder
     * =================================================================== */

    @Nested
    @DisplayName("ChannelOrder 엔티티")
    class ChannelOrderTests {

        // 주문 엔티티의 핵심 표시/식별 필드가 빌더 입력 그대로 유지되는지 본다.
        @Test
        @DisplayName("Builder로 ChannelOrder를 생성하면 각 필드가 정상적으로 설정된다")
        void builder_setsAllFields() {
            // given & when
            ChannelOrder order = ChannelOrder.builder()
                    .orderId("order-001")
                    .channelOrderNo("#1001")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-A")
                    .receiverName("홍길동")
                    .receiverPhoneNo("010-1234-5678")
                    .shipToAddress1("123 Main St")
                    .shipToCity("Seoul")
                    .shipToZipCode("12345")
                    .build();

            // then
            assertThat(order.getOrderId()).isEqualTo("order-001");
            assertThat(order.getChannelOrderNo()).isEqualTo("#1001");
            assertThat(order.getOrderChannel()).isEqualTo(OrderChannel.SHOPIFY);
            assertThat(order.getSellerId()).isEqualTo("seller-A");
            assertThat(order.getReceiverName()).isEqualTo("홍길동");
            assertThat(order.getItems()).isEmpty();
        }

        @Test
        @DisplayName("addItem()을 호출하면 items 리스트에 아이템이 추가된다")
        void addItem_addsToList() {
            // given
            ChannelOrder order = ChannelOrder.builder()
                    .orderId("order-001")
                    .sellerId("seller-A")
                    .build();

            ChannelOrderItemId itemId = new ChannelOrderItemId("order-001", "sku-001");
            ChannelOrderItem item = ChannelOrderItem.builder()
                    .id(itemId)
                    .channelOrder(order)
                    .quantity(3)
                    .productNameSnapshot("테스트 상품")
                    .build();

            // when
            order.addItem(item);

            // then
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getItems().get(0).getProductNameSnapshot()).isEqualTo("테스트 상품");
        }

        @Test
        @DisplayName("여러 아이템을 추가하면 모두 items 리스트에 누적된다")
        void addItem_multipleItems_allAdded() {
            // 연속 addItem 호출이 누적 append로 동작해야 한다.
            // given
            ChannelOrder order = ChannelOrder.builder()
                    .orderId("order-002")
                    .sellerId("seller-B")
                    .build();

            ChannelOrderItem item1 = ChannelOrderItem.builder()
                    .id(new ChannelOrderItemId("order-002", "sku-A"))
                    .channelOrder(order)
                    .quantity(2)
                    .productNameSnapshot("상품A")
                    .build();
            ChannelOrderItem item2 = ChannelOrderItem.builder()
                    .id(new ChannelOrderItemId("order-002", "sku-B"))
                    .channelOrder(order)
                    .quantity(1)
                    .productNameSnapshot("상품B")
                    .build();

            // when
            order.addItem(item1);
            order.addItem(item2);

            // then
            assertThat(order.getItems()).hasSize(2);
        }
    }

    /* ===================================================================
     * ChannelOrderItem
     * =================================================================== */

    @Nested
    @DisplayName("ChannelOrderItem 엔티티")
    class ChannelOrderItemTests {

        // 작업 수량 기본값은 아직 피킹/포장 전 상태를 뜻한다.
        @Test
        @DisplayName("Builder로 ChannelOrderItem을 생성하면 기본 수량 값이 0이다")
        void builder_defaultQuantities_areZero() {
            // given
            ChannelOrderItemId id = new ChannelOrderItemId("order-001", "sku-001");
            ChannelOrder order = ChannelOrder.builder().orderId("order-001").sellerId("S").build();

            // when
            ChannelOrderItem item = ChannelOrderItem.builder()
                    .id(id)
                    .channelOrder(order)
                    .quantity(5)
                    .build();

            // then
            assertThat(item.getQuantity()).isEqualTo(5);
            assertThat(item.getPickedQuantity()).isZero();
            assertThat(item.getPackedQuantity()).isZero();
        }
    }

    /* ===================================================================
     * ChannelApiId (복합 키)
     * =================================================================== */

    @Nested
    @DisplayName("ChannelApiId 복합 키")
    class ChannelApiIdTests {

        // 복합 키는 값 객체이므로 equals/hashCode 계약이 중요하다.
        @Test
        @DisplayName("동일한 sellerId와 channelName이면 equals/hashCode가 같아야 한다")
        void equalIds_haveEqualHashCodes() {
            ChannelApiId id1 = new ChannelApiId("seller-A", "SHOPIFY");
            ChannelApiId id2 = new ChannelApiId("seller-A", "SHOPIFY");

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("다른 sellerId이면 equals가 false이다")
        void differentSellerId_notEqual() {
            ChannelApiId id1 = new ChannelApiId("seller-A", "SHOPIFY");
            ChannelApiId id2 = new ChannelApiId("seller-B", "SHOPIFY");

            assertThat(id1).isNotEqualTo(id2);
        }
    }

    /* ===================================================================
     * EasypostShipmentInvoice
     * =================================================================== */

    @Nested
    @DisplayName("EasypostShipmentInvoice 엔티티")
    class EasypostShipmentInvoiceTests {

        // 송장 엔티티는 추적/금액 필드 보존이 핵심이다.
        @Test
        @DisplayName("Builder로 Invoice를 생성하면 invoiceNo와 carrierType이 올바르게 설정된다")
        void builder_setsInvoiceFields() {
            // given & when
            EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                    .invoiceNo("INV-001")
                    .carrierType(CarrierType.UPS)
                    .freightChargeAmt(1500)
                    .shipToAddress("123 Main St, New York, NY, 10001, US")
                    .trackingUrl("https://track.easypost.com/ABC123")
                    .labelFileUrl("https://cdn.easypost.com/label/ABC123.pdf")
                    .build();

            // then
            assertThat(invoice.getInvoiceNo()).isEqualTo("INV-001");
            assertThat(invoice.getCarrierType()).isEqualTo(CarrierType.UPS);
            assertThat(invoice.getFreightChargeAmt()).isEqualTo(1500);
        }

        @Test
        @DisplayName("CarrierType 열거형은 USPS, UPS, FEDEX 세 가지 값을 가진다")
        void carrierType_hasThreeValues() {
            assertThat(CarrierType.values()).containsExactlyInAnyOrder(
                    CarrierType.USPS, CarrierType.UPS, CarrierType.FEDEX
            );
        }
    }

    /* ===================================================================
     * OrderChannel (Enum)
     * =================================================================== */

    @Nested
    @DisplayName("OrderChannel 열거형")
    class OrderChannelTests {

        // 외부 저장값과 enum name 매핑이 일치해야 한다.
        @Test
        @DisplayName("OrderChannel.SHOPIFY는 name()이 'SHOPIFY'여야 한다")
        void shopify_name() {
            assertThat(OrderChannel.SHOPIFY.name()).isEqualTo("SHOPIFY");
        }
    }
}
