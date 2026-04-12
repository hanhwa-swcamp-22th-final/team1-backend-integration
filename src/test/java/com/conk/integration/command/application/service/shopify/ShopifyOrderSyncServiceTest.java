package com.conk.integration.command.application.service.shopify;

import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyOrderResponse;
import com.conk.integration.command.application.service.shopify.ShopifyOrderSyncService;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyOrderClient;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.query.service.ChannelApiQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;

// Shopify 주문 동기화 서비스의 GraphQL 매핑, 중복 방지, 예외 전파를 검증한다.
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyOrderSyncService 테스트")
class ShopifyOrderSyncServiceTest {

    @Mock private ShopifyOrderClient shopifyOrderClient;
    @Mock private ChannelOrderRepository channelOrderRepository;
    @Mock private ChannelApiQueryService channelApiQueryService;

    @InjectMocks
    private ShopifyOrderSyncService syncService;

    private static final String SELLER_ID = "seller-001";

    private ChannelCredential buildCredential() {
        ChannelCredential dto = new ChannelCredential();
        dto.setStoreName("conktest");
        dto.setAccessToken("test-token");
        return dto;
    }

    // ─────────────────────────────────────────────────────────
    // 저장 / 중복 방지
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("신규 주문이 주어지면 주문 동기화를 수행했을 때 channel_order 테이블에 저장해야 한다")
    void syncOrders_savesNewOrderToRepository() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/4502818226334", "#1001", "2024-01-15T10:00:00-05:00",
                "gid://shopify/FulfillmentOrder/99");
        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("4502818226334")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        then(channelOrderRepository).should(times(1)).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("이미 저장된 주문이 주어지면 주문 동기화를 수행했을 때 중복 저장을 건너뛰어야 한다")
    void syncOrders_skipsDuplicateOrder() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/4502818226334", "#1001", "2024-01-15T10:00:00-05:00",
                "gid://shopify/FulfillmentOrder/99");
        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("4502818226334")).willReturn(true);

        syncService.syncOrders(SELLER_ID);

        then(channelOrderRepository).should(never()).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("기존 주문과 신규 주문이 함께 주어지면 주문 동기화를 수행했을 때 신규 주문만 저장해야 한다")
    void syncOrders_savesOnlyNewOrders_whenMixedExistence() {
        ShopifyOrderResponse.OrderNode existing = buildOrderNode(
                "gid://shopify/Order/1000", "#1000", "2024-01-10T00:00:00-05:00", null);
        ShopifyOrderResponse.OrderNode newOne = buildOrderNode(
                "gid://shopify/Order/1001", "#1001", "2024-01-14T00:00:00-05:00", null);
        ShopifyOrderResponse.OrderNode newTwo = buildOrderNode(
                "gid://shopify/Order/1002", "#1002", "2024-01-15T00:00:00-05:00", null);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(existing, newOne, newTwo));
        given(channelOrderRepository.existsById("1000")).willReturn(true);
        given(channelOrderRepository.existsById("1001")).willReturn(false);
        given(channelOrderRepository.existsById("1002")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        then(channelOrderRepository).should(times(2)).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("API 주문 목록이 비어 있으면 주문 동기화를 수행했을 때 save를 호출하지 않아야 한다")
    void syncOrders_doesNotSave_whenNoOrdersReturned() {
        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of());

        syncService.syncOrders(SELLER_ID);

        then(channelOrderRepository).should(never()).save(any(ChannelOrder.class));
    }

    // ─────────────────────────────────────────────────────────
    // 필드 매핑
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Shopify 주문 정보가 주어지면 주문 동기화를 수행했을 때 GID 숫자 ID와 주소 및 채널 필드를 정확히 매핑해야 한다")
    void syncOrders_mapsFieldsCorrectly() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/4502818226334", "#1001", "2024-01-15T10:00:00-05:00",
                "gid://shopify/FulfillmentOrder/99");
        node.getShippingAddress().setName("Jane Smith");
        node.getShippingAddress().setAddress1("456 Oak Ave");
        node.getShippingAddress().setAddress2("Suite 100");
        node.getShippingAddress().setCity("Seattle");
        node.getShippingAddress().setProvinceCode("WA");
        node.getShippingAddress().setZip("98101");
        node.getShippingAddress().setPhone("206-555-1234");

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("4502818226334")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        ChannelOrder saved = captor.getValue();

        assertThat(saved.getOrderId()).isEqualTo("4502818226334");
        assertThat(saved.getChannelOrderNo()).isEqualTo("#1001");
        assertThat(saved.getOrderChannel()).isEqualTo(OrderChannel.SHOPIFY);
        assertThat(saved.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(saved.getReceiverName()).isEqualTo("Jane Smith");
        assertThat(saved.getReceiverPhoneNo()).isEqualTo("206-555-1234");
        assertThat(saved.getShipToAddress1()).isEqualTo("456 Oak Ave");
        assertThat(saved.getShipToAddress2()).isEqualTo("Suite 100");
        assertThat(saved.getShipToCity()).isEqualTo("Seattle");
        assertThat(saved.getShipToState()).isEqualTo("WA");
        assertThat(saved.getShipToZipCode()).isEqualTo("98101");
    }

    @Test
    @DisplayName("fulfillmentOrders가 포함된 주문이 주어지면 주문 동기화를 수행했을 때 첫 번째 GID를 fulfillmentOrderId로 저장해야 한다")
    void syncOrders_savesFulfillmentOrderId() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/5000", "#5000", "2024-01-20T10:00:00-05:00",
                "gid://shopify/FulfillmentOrder/777");

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("5000")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());

        assertThat(captor.getValue().getFulfillmentOrderId())
                .isEqualTo("gid://shopify/FulfillmentOrder/777");
    }

    @Test
    @DisplayName("fulfillmentOrders가 없으면 주문 동기화를 수행했을 때 fulfillmentOrderId를 null로 저장해야 한다")
    void syncOrders_savesFulfillmentOrderIdAsNull_whenFulfillmentOrdersEmpty() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/6000", "#6000", "2024-01-20T10:00:00-05:00", null);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("6000")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());

        assertThat(captor.getValue().getFulfillmentOrderId()).isNull();
    }

    @Test
    @DisplayName("shippingAddress가 null인 주문이 주어지면 주문 동기화를 수행했을 때 예외 없이 저장해야 한다")
    void syncOrders_savesOrder_whenShippingAddressIsNull() {
        ShopifyOrderResponse.OrderNode node = new ShopifyOrderResponse.OrderNode();
        node.setId("gid://shopify/Order/9999");
        node.setName("#9999");
        node.setCreatedAt("2025-01-20T10:00:00-05:00");
        node.setShippingAddress(null);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("9999")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        ChannelOrder saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo("9999");
        assertThat(saved.getReceiverName()).isNull();
        assertThat(saved.getShipToAddress1()).isNull();
    }

    @Test
    @DisplayName("createdAt이 null인 주문이 주어지면 주문 동기화를 수행했을 때 orderedAt을 null로 저장해야 한다")
    void syncOrders_savesOrder_whenCreatedAtIsNull() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/8888", "#8888", null, null);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("8888")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getOrderedAt()).isNull();
    }

    // ─────────────────────────────────────────────────────────
    // 예외 전파
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createdAt이 공백 문자열인 주문이 주어지면 주문 동기화를 수행했을 때 orderedAt을 null로 저장해야 한다")
    void syncOrders_savesOrder_whenCreatedAtIsBlank() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/7777", "#7777", "   ", null);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("7777")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getOrderedAt()).isNull();
    }

    @Test
    @DisplayName("주문 저장 중 예외가 발생하면 주문 동기화를 수행했을 때 호출자에게 예외를 전파해야 한다")
    void syncOrders_propagatesException_whenRepositorySaveThrows() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/1111", "#1111", "2024-01-15T10:00:00-05:00", null);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("1111")).willReturn(false);
        given(channelOrderRepository.save(any())).willThrow(new RuntimeException("DB 저장 실패"));

        assertThatThrownBy(() -> syncService.syncOrders("seller-001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 저장 실패");
    }

    @Test
    @DisplayName("API 호출에서 401 오류가 발생하면 주문 동기화를 수행했을 때 호출자에게 예외를 전파해야 한다")
    void syncOrders_propagatesException_whenApiClientThrowsUnauthorized() {
        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString()))
                .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> syncService.syncOrders("seller-001"))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        then(channelOrderRepository).should(never()).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("API 호출에서 500 오류가 발생하면 주문 동기화를 수행했을 때 호출자에게 예외를 전파해야 한다")
    void syncOrders_propagatesException_whenApiClientThrowsServerError() {
        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString()))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> syncService.syncOrders("seller-001"))
                .isInstanceOf(HttpServerErrorException.class);
        then(channelOrderRepository).should(never()).save(any(ChannelOrder.class));
    }

    // ─────────────────────────────────────────────────────────
    // ChannelOrderItem 저장
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("line item에 sku가 있으면 주문 동기화를 수행했을 때 sku를 skuId로 사용해 주문 아이템을 저장해야 한다")
    void syncOrders_savesChannelOrderItem_whenSkuPresent() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/2000", "#2000", "2024-02-01T10:00:00-05:00", null);
        node.setLineItems(buildLineItemConnection("SKU-001", "Product A", 3, null));

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("2000")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        ChannelOrder saved = captor.getValue();

        assertThat(saved.getItems()).hasSize(1);
        ChannelOrderItem item = saved.getItems().get(0);
        assertThat(item.getId().getSkuId()).isEqualTo("SKU-001");
        assertThat(item.getProductNameSnapshot()).isEqualTo("Product A");
        assertThat(item.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("sku가 비어 있고 variant id가 있으면 주문 동기화를 수행했을 때 variant GID 끝 숫자를 skuId로 사용해야 한다")
    void syncOrders_usesVariantIdAsSkuId_whenSkuIsBlank() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/3000", "#3000", "2024-02-01T10:00:00-05:00", null);
        node.setLineItems(buildLineItemConnection("", "Product B", 1, "gid://shopify/ProductVariant/99999"));

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("3000")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());

        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getItems().get(0).getId().getSkuId()).isEqualTo("99999");
    }

    @Test
    @DisplayName("lineItems가 null이어도 주문 동기화를 수행했을 때 주문 자체는 정상 저장해야 한다")
    void syncOrders_savesOrderWithoutItems_whenLineItemsIsNull() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/4000", "#4000", "2024-02-01T10:00:00-05:00", null);
        node.setLineItems(null);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("4000")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getItems()).isEmpty();
    }

    @Test
    @DisplayName("sku와 variant id가 모두 없으면 주문 동기화를 수행했을 때 해당 line item 저장을 건너뛰어야 한다")
    void syncOrders_skipsLineItemWhenSkuAndVariantIdMissing() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/4100", "#4100", "2024-02-01T10:00:00-05:00", null);
        node.setLineItems(buildLineItemConnection(null, "Product Without SKU", 1, null));

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("4100")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getItems()).isEmpty();
    }

    @Test
    @DisplayName("fulfillment order node id가 null이면 주문 동기화를 수행했을 때 fulfillmentOrderId를 null로 저장해야 한다")
    void syncOrders_returnsNullFulfillmentOrderIdWhenFulfillmentOrderNodeIdMissing() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/6100", "#6100", "2024-02-01T10:00:00-05:00", null);
        ShopifyOrderResponse.FulfillmentOrderNode fulfillmentOrderNode = new ShopifyOrderResponse.FulfillmentOrderNode();
        fulfillmentOrderNode.setId(null);
        ShopifyOrderResponse.FulfillmentOrderEdge fulfillmentOrderEdge = new ShopifyOrderResponse.FulfillmentOrderEdge();
        fulfillmentOrderEdge.setNode(fulfillmentOrderNode);
        ShopifyOrderResponse.FulfillmentOrderConnection connection = new ShopifyOrderResponse.FulfillmentOrderConnection();
        connection.setEdges(List.of(fulfillmentOrderEdge));
        node.setFulfillmentOrders(connection);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("6100")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getFulfillmentOrderId()).isNull();
    }

    @Test
    @DisplayName("기존 주문과 신규 주문이 함께 주어지면 주문 동기화를 수행했을 때 savedCount와 skippedCount를 정확히 반환해야 한다")
    void syncOrders_returnsSavedAndSkippedCount() {
        ShopifyOrderResponse.OrderNode existing = buildOrderNode(
                "gid://shopify/Order/5000", "#5000", "2024-02-01T10:00:00-05:00", null);
        ShopifyOrderResponse.OrderNode newOne = buildOrderNode(
                "gid://shopify/Order/5001", "#5001", "2024-02-01T10:00:00-05:00", null);

        given(channelApiQueryService.findChannelCredential(SELLER_ID, "SHOPIFY")).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(existing, newOne));
        given(channelOrderRepository.existsById("5000")).willReturn(true);
        given(channelOrderRepository.existsById("5001")).willReturn(false);

        ChannelOrderSyncResponse result = syncService.syncOrders(SELLER_ID);

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getOrders()).hasSize(1);
        assertThat(result.getOrders().get(0).getOrderId()).isEqualTo("5001");
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private ShopifyOrderResponse.OrderNode buildOrderNode(String gidOrderId, String name,
                                                          String createdAt, String gidFulfillmentOrderId) {
        ShopifyOrderResponse.OrderNode node = new ShopifyOrderResponse.OrderNode();
        node.setId(gidOrderId);
        node.setName(name);
        node.setCreatedAt(createdAt);

        ShopifyOrderResponse.ShippingAddress addr = new ShopifyOrderResponse.ShippingAddress();
        addr.setName("John Doe");
        addr.setAddress1("123 Main St");
        addr.setCity("New York");
        addr.setProvinceCode("NY");
        addr.setZip("10001");
        addr.setPhone("555-1234");
        node.setShippingAddress(addr);

        if (gidFulfillmentOrderId != null) {
            ShopifyOrderResponse.FulfillmentOrderNode foNode = new ShopifyOrderResponse.FulfillmentOrderNode();
            foNode.setId(gidFulfillmentOrderId);
            ShopifyOrderResponse.FulfillmentOrderEdge foEdge = new ShopifyOrderResponse.FulfillmentOrderEdge();
            foEdge.setNode(foNode);
            ShopifyOrderResponse.FulfillmentOrderConnection foConn = new ShopifyOrderResponse.FulfillmentOrderConnection();
            foConn.setEdges(List.of(foEdge));
            node.setFulfillmentOrders(foConn);
        }

        return node;
    }

    private ShopifyOrderResponse.LineItemConnection buildLineItemConnection(
            String sku, String title, int quantity, String gidVariantId) {

        ShopifyOrderResponse.LineItemNode lineItemNode = new ShopifyOrderResponse.LineItemNode();
        lineItemNode.setSku(sku);
        lineItemNode.setTitle(title);
        lineItemNode.setQuantity(quantity);

        if (gidVariantId != null) {
            ShopifyOrderResponse.VariantNode variantNode = new ShopifyOrderResponse.VariantNode();
            variantNode.setId(gidVariantId);
            lineItemNode.setVariant(variantNode);
        }

        ShopifyOrderResponse.LineItemEdge edge = new ShopifyOrderResponse.LineItemEdge();
        edge.setNode(lineItemNode);

        ShopifyOrderResponse.LineItemConnection connection = new ShopifyOrderResponse.LineItemConnection();
        connection.setEdges(List.of(edge));
        return connection;
    }
}
