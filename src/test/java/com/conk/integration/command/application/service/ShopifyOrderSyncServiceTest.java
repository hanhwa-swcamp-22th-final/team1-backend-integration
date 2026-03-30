package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.response.ShopifyOrderDto;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.ShopifyApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// Shopify 주문 동기화 서비스의 매핑, 중복 방지, 예외 전파를 검증한다.
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyOrderSyncService Tests")
class ShopifyOrderSyncServiceTest {

    @Mock private ShopifyApiClient shopifyApiClient;
    @Mock private ChannelOrderRepository channelOrderRepository;

    @InjectMocks
    private ShopifyOrderSyncService syncService;

    // ─────────────────────────────────────────────────────────
    // Cycle 2: Shopify 주문 동기화 서비스
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[RED→GREEN] 신규 주문을 channel_order 테이블에 저장")
    void syncOrders_savesNewOrderToRepository() {
        // given
        ShopifyOrderDto dto = buildOrderDto(4502818226334L, "#1001", "2024-01-15T10:00:00-05:00");
        given(shopifyApiClient.getOrders()).willReturn(List.of(dto));
        given(channelOrderRepository.existsById("4502818226334")).willReturn(false);

        // when
        syncService.syncOrders("seller-001");

        // then
        verify(channelOrderRepository, times(1)).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("[RED→GREEN] 이미 저장된 주문은 skip (중복 저장 방지)")
    void syncOrders_skipsDuplicateOrder() {
        // given
        ShopifyOrderDto dto = buildOrderDto(4502818226334L, "#1001", "2024-01-15T10:00:00-05:00");
        given(shopifyApiClient.getOrders()).willReturn(List.of(dto));
        given(channelOrderRepository.existsById("4502818226334")).willReturn(true);

        // when
        syncService.syncOrders("seller-001");

        // then
        verify(channelOrderRepository, never()).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("[RED→GREEN] 여러 주문 중 신규 건만 저장")
    void syncOrders_savesOnlyNewOrders_whenMixedExistence() {
        // given
        ShopifyOrderDto existing = buildOrderDto(1000L, "#1000", "2024-01-10T00:00:00-05:00");
        ShopifyOrderDto newOne   = buildOrderDto(1001L, "#1001", "2024-01-14T00:00:00-05:00");
        ShopifyOrderDto newTwo   = buildOrderDto(1002L, "#1002", "2024-01-15T00:00:00-05:00");

        given(shopifyApiClient.getOrders()).willReturn(List.of(existing, newOne, newTwo));
        given(channelOrderRepository.existsById("1000")).willReturn(true);
        given(channelOrderRepository.existsById("1001")).willReturn(false);
        given(channelOrderRepository.existsById("1002")).willReturn(false);

        // when
        syncService.syncOrders("seller-001");

        // then
        verify(channelOrderRepository, times(2)).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("[RED→GREEN] 저장된 ChannelOrder 필드가 Shopify 응답과 올바르게 매핑")
    void syncOrders_mapsFieldsCorrectly() {
        // 주소 필드를 모두 채워 채널 주문 엔티티로의 매핑 누락이 없는지 본다.
        ShopifyOrderDto dto = buildOrderDto(4502818226334L, "#1001", "2024-01-15T10:00:00-05:00");
        dto.getShippingAddress().setName("Jane Smith");
        dto.getShippingAddress().setAddress1("456 Oak Ave");
        dto.getShippingAddress().setAddress2("Suite 100");
        dto.getShippingAddress().setCity("Seattle");
        dto.getShippingAddress().setProvinceCode("WA");
        dto.getShippingAddress().setZip("98101");
        dto.getShippingAddress().setPhone("206-555-1234");

        given(shopifyApiClient.getOrders()).willReturn(List.of(dto));
        given(channelOrderRepository.existsById("4502818226334")).willReturn(false);

        // when
        syncService.syncOrders("seller-001");

        // then
        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        verify(channelOrderRepository).save(captor.capture());

        ChannelOrder saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo("4502818226334");
        assertThat(saved.getChannelOrderNo()).isEqualTo("#1001");
        assertThat(saved.getOrderChannel()).isEqualTo(OrderChannel.SHOPIFY);
        assertThat(saved.getSellerId()).isEqualTo("seller-001");
        assertThat(saved.getReceiverName()).isEqualTo("Jane Smith");
        assertThat(saved.getReceiverPhoneNo()).isEqualTo("206-555-1234");
        assertThat(saved.getShipToAddress1()).isEqualTo("456 Oak Ave");
        assertThat(saved.getShipToAddress2()).isEqualTo("Suite 100");
        assertThat(saved.getShipToCity()).isEqualTo("Seattle");
        assertThat(saved.getShipToState()).isEqualTo("WA");
        assertThat(saved.getShipToZipCode()).isEqualTo("98101");
    }

    @Test
    @DisplayName("[RED→GREEN] API 주문 목록이 빈 경우 save 미호출")
    void syncOrders_doesNotSave_whenNoOrdersReturned() {
        // given
        given(shopifyApiClient.getOrders()).willReturn(List.of());

        // when
        syncService.syncOrders("seller-001");

        // then
        verify(channelOrderRepository, never()).save(any(ChannelOrder.class));
    }

    // ─────────────────────────────────────────────────────────
    // Cycle 3: 예외 상황
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] API 호출 시 401이 발생하면 예외가 호출자에게 전파")
    void syncOrders_propagatesException_whenApiClientThrowsUnauthorized() {
        // given
        given(shopifyApiClient.getOrders())
                .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> syncService.syncOrders("seller-001"))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(channelOrderRepository, never()).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("[예외] API 호출 시 500이 발생하면 예외가 호출자에게 전파")
    void syncOrders_propagatesException_whenApiClientThrowsServerError() {
        // given
        given(shopifyApiClient.getOrders())
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // when & then
        assertThatThrownBy(() -> syncService.syncOrders("seller-001"))
                .isInstanceOf(HttpServerErrorException.class);
        verify(channelOrderRepository, never()).save(any(ChannelOrder.class));
    }

    @Test
    @DisplayName("[예외] shippingAddress가 null인 주문도 NPE 없이 저장 성공 (null 필드로 저장)")
    void syncOrders_savesOrder_whenShippingAddressIsNull() {
        // 외부 API 데이터가 불완전해도 최소 주문 저장은 가능해야 한다.
        ShopifyOrderDto dto = new ShopifyOrderDto();
        dto.setId(9999L);
        dto.setName("#9999");
        dto.setCreatedAt("2025-01-20T10:00:00-05:00");
        dto.setFinancialStatus("paid");
        dto.setShippingAddress(null);

        given(shopifyApiClient.getOrders()).willReturn(List.of(dto));
        given(channelOrderRepository.existsById("9999")).willReturn(false);

        // when
        syncService.syncOrders("seller-001");

        // then
        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        verify(channelOrderRepository).save(captor.capture());
        ChannelOrder saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo("9999");
        assertThat(saved.getReceiverName()).isNull();
        assertThat(saved.getReceiverPhoneNo()).isNull();
        assertThat(saved.getShipToAddress1()).isNull();
        assertThat(saved.getShipToCity()).isNull();
    }

    @Test
    @DisplayName("[예외] createdAt이 null이면 orderedAt=null로 저장 성공")
    void syncOrders_savesOrder_whenCreatedAtIsNull() {
        // given
        ShopifyOrderDto dto = buildOrderDto(8888L, "#8888", null);
        given(shopifyApiClient.getOrders()).willReturn(List.of(dto));
        given(channelOrderRepository.existsById("8888")).willReturn(false);

        // when
        syncService.syncOrders("seller-001");

        // then
        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        verify(channelOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderedAt()).isNull();
    }

    @Test
    @DisplayName("[예외] createdAt이 공백 문자열이면 orderedAt=null로 저장 성공")
    void syncOrders_savesOrder_whenCreatedAtIsBlank() {
        // given
        ShopifyOrderDto dto = buildOrderDto(7777L, "#7777", "   ");
        given(shopifyApiClient.getOrders()).willReturn(List.of(dto));
        given(channelOrderRepository.existsById("7777")).willReturn(false);

        // when
        syncService.syncOrders("seller-001");

        // then
        ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
        verify(channelOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderedAt()).isNull();
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    // syncOrders가 소비하는 최소 Shopify 주문 응답 fixture다.
    private ShopifyOrderDto buildOrderDto(Long id, String name, String createdAt) {
        ShopifyOrderDto dto = new ShopifyOrderDto();
        dto.setId(id);
        dto.setName(name);
        dto.setCreatedAt(createdAt);
        dto.setFinancialStatus("paid");

        ShopifyOrderDto.ShippingAddress addr = new ShopifyOrderDto.ShippingAddress();
        addr.setName("John Doe");
        addr.setAddress1("123 Main St");
        addr.setCity("New York");
        addr.setProvinceCode("NY");
        addr.setZip("10001");
        addr.setPhone("555-1234");
        dto.setShippingAddress(addr);

        return dto;
    }
}
