package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderResponse;
import com.conk.integration.command.application.service.shopify.ShopifyOrderSyncService;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.ShopifyOrderClient;
import com.conk.integration.query.dto.ShopifyCredentialDto;
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
@DisplayName("ShopifyOrderSyncService Tests")
class ShopifyOrderSyncServiceTest {

    @Mock private ShopifyOrderClient shopifyOrderClient;
    @Mock private ChannelOrderRepository channelOrderRepository;
    @Mock private ChannelApiQueryService channelApiQueryService;

    @InjectMocks
    private ShopifyOrderSyncService syncService;

    private static final String SELLER_ID = "seller-001";

    private ShopifyCredentialDto buildCredential() {
        ShopifyCredentialDto dto = new ShopifyCredentialDto();
        dto.setStoreName("conktest");
        dto.setAccessToken("test-token");
        return dto;
    }

    // ─────────────────────────────────────────────────────────
    // 저장 / 중복 방지
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("신규 주문을 channel_order 테이블에 저장한다")
    void syncOrders_savesNewOrderToRepository() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/4502818226334", "#1001", "2024-01-15T10:00:00-05:00",
                "gid://shopify/FulfillmentOrder/99");
        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("4502818226334")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        then(channelOrderRepository).should(times(1)).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("이미 저장된 주문은 skip한다 (중복 저장 방지)")
    void syncOrders_skipsDuplicateOrder() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/4502818226334", "#1001", "2024-01-15T10:00:00-05:00",
                "gid://shopify/FulfillmentOrder/99");
        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("4502818226334")).willReturn(true);

        syncService.syncOrders(SELLER_ID);

        then(channelOrderRepository).should(never()).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("여러 주문 중 신규 건만 저장한다")
    void syncOrders_savesOnlyNewOrders_whenMixedExistence() {
        ShopifyOrderResponse.OrderNode existing = buildOrderNode(
                "gid://shopify/Order/1000", "#1000", "2024-01-10T00:00:00-05:00", null);
        ShopifyOrderResponse.OrderNode newOne = buildOrderNode(
                "gid://shopify/Order/1001", "#1001", "2024-01-14T00:00:00-05:00", null);
        ShopifyOrderResponse.OrderNode newTwo = buildOrderNode(
                "gid://shopify/Order/1002", "#1002", "2024-01-15T00:00:00-05:00", null);

        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(existing, newOne, newTwo));
        given(channelOrderRepository.existsById("1000")).willReturn(true);
        given(channelOrderRepository.existsById("1001")).willReturn(false);
        given(channelOrderRepository.existsById("1002")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        then(channelOrderRepository).should(times(2)).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("API 주문 목록이 빈 경우 save를 호출하지 않는다")
    void syncOrders_doesNotSave_whenNoOrdersReturned() {
        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of());

        syncService.syncOrders(SELLER_ID);

        then(channelOrderRepository).should(never()).save(any(ChannelOrder.class));
    }

    // ─────────────────────────────────────────────────────────
    // 필드 매핑
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GID에서 숫자 ID를 추출하고 주소/채널 필드를 정확히 매핑한다")
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

        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
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
    @DisplayName("fulfillmentOrders 첫 번째 항목 GID가 fulfillmentOrderId로 저장된다")
    void syncOrders_savesFulfillmentOrderId() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/5000", "#5000", "2024-01-20T10:00:00-05:00",
                "gid://shopify/FulfillmentOrder/777");

        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("5000")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());

        assertThat(captor.getValue().getFulfillmentOrderId())
                .isEqualTo("gid://shopify/FulfillmentOrder/777");
    }

    @Test
    @DisplayName("fulfillmentOrders가 없으면 fulfillmentOrderId=null로 저장된다")
    void syncOrders_savesFulfillmentOrderIdAsNull_whenFulfillmentOrdersEmpty() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/6000", "#6000", "2024-01-20T10:00:00-05:00", null);

        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("6000")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());

        assertThat(captor.getValue().getFulfillmentOrderId()).isNull();
    }

    @Test
    @DisplayName("shippingAddress가 null인 주문도 NPE 없이 저장 성공")
    void syncOrders_savesOrder_whenShippingAddressIsNull() {
        ShopifyOrderResponse.OrderNode node = new ShopifyOrderResponse.OrderNode();
        node.setId("gid://shopify/Order/9999");
        node.setName("#9999");
        node.setCreatedAt("2025-01-20T10:00:00-05:00");
        node.setShippingAddress(null);

        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
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
    @DisplayName("createdAt이 null이면 orderedAt=null로 저장 성공")
    void syncOrders_savesOrder_whenCreatedAtIsNull() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/8888", "#8888", null, null);

        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
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
    @DisplayName("createdAt이 공백 문자열이면 orderedAt=null로 저장된다")
    void syncOrders_savesOrder_whenCreatedAtIsBlank() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/7777", "#7777", "   ", null);

        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("7777")).willReturn(false);

        syncService.syncOrders(SELLER_ID);

        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        then(channelOrderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getOrderedAt()).isNull();
    }

    @Test
    @DisplayName("repository save 중 예외 발생 시 호출자에게 전파된다")
    void syncOrders_propagatesException_whenRepositorySaveThrows() {
        ShopifyOrderResponse.OrderNode node = buildOrderNode(
                "gid://shopify/Order/1111", "#1111", "2024-01-15T10:00:00-05:00", null);

        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));
        given(channelOrderRepository.existsById("1111")).willReturn(false);
        given(channelOrderRepository.save(any())).willThrow(new RuntimeException("DB 저장 실패"));

        assertThatThrownBy(() -> syncService.syncOrders("seller-001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 저장 실패");
    }

    @Test
    @DisplayName("API 호출 시 401이 발생하면 예외가 호출자에게 전파된다")
    void syncOrders_propagatesException_whenApiClientThrowsUnauthorized() {
        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString()))
                .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> syncService.syncOrders("seller-001"))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        then(channelOrderRepository).should(never()).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("API 호출 시 500이 발생하면 예외가 호출자에게 전파된다")
    void syncOrders_propagatesException_whenApiClientThrowsServerError() {
        given(channelApiQueryService.findShopifyCredential(SELLER_ID)).willReturn(buildCredential());
        given(shopifyOrderClient.getOrders(anyString(), anyString()))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> syncService.syncOrders("seller-001"))
                .isInstanceOf(HttpServerErrorException.class);
        then(channelOrderRepository).should(never()).save(any(ChannelOrder.class));
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
}