package com.conk.integration.command.application.service.shopify;

import com.conk.integration.command.application.dto.request.ShopifyFulfillmentRequest;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.infrastructure.service.ShopifyFulfillmentApiClient;
import com.conk.integration.query.dto.FulfillmentTargetDto;
import com.conk.integration.query.dto.ShopifyCredentialDto;
import com.conk.integration.query.mapper.ChannelApiMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

// Shopify sender가 채널 지원 여부와 요청 변환을 올바르게 수행하는지 검증한다.
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyFulfillmentSender 단위 테스트")
class ShopifyFulfillmentSenderTest {

    @Mock
    private ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;

    @Mock
    private ChannelApiMapper channelApiMapper;

    @InjectMocks
    private ShopifyFulfillmentSender sender;

    private static final String SELLER_ID = "seller-001";
    private static final String STORE_NAME = "conktest";
    private static final String ACCESS_TOKEN = "test-token";

    // ─────────────────────────────────────────────────────────
    // supports()
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("supports() — SHOPIFY 채널만 true를 반환한다")
    void supports_returnsTrueOnlyForShopify() {
        assertThat(sender.supports(OrderChannel.SHOPIFY)).isTrue();
        assertThat(sender.supports(OrderChannel.AMAZON)).isFalse();
        assertThat(sender.supports(OrderChannel.MANUAL)).isFalse();
    }

    // ─────────────────────────────────────────────────────────
    // send() — 단건
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() — 주문/송장을 Shopify fulfillment 요청으로 변환해 API를 호출한다")
    void send_buildsRequestAndCallsApiClient() {
        given(channelApiMapper.findShopifyCredential(SELLER_ID)).willReturn(cred());

        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORD-001")
                .channelOrderNo("5678901234")
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId(SELLER_ID)
                .invoiceNo("INV-001")
                .build();
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-001")
                .carrierType(CarrierType.UPS)
                .build();

        sender.send(order, invoice);

        ArgumentCaptor<ShopifyFulfillmentRequest> requestCaptor =
                ArgumentCaptor.forClass(ShopifyFulfillmentRequest.class);
        verify(shopifyFulfillmentApiClient).createFulfillment(
                eq(STORE_NAME), eq(ACCESS_TOKEN), anyString(), requestCaptor.capture());
        ShopifyFulfillmentRequest request = requestCaptor.getValue();

        assertThat(request.getFulfillment().getTrackingInfo().getNumber()).isEqualTo("INV-001");
        assertThat(request.getFulfillment().getTrackingInfo().getCompany()).isEqualTo("UPS");
        assertThat(request.getFulfillment().isNotifyCustomer()).isTrue();
    }

    @Test
    @DisplayName("send() — Shopify API client 예외를 그대로 전파한다")
    void send_propagatesExceptionFromClient() {
        given(channelApiMapper.findShopifyCredential(SELLER_ID)).willReturn(cred());

        ChannelOrder order = ChannelOrder.builder()
                .orderId("ORD-004")
                .channelOrderNo("5678901234")
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId(SELLER_ID)
                .invoiceNo("INV-004")
                .build();
        EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                .invoiceNo("INV-004")
                .carrierType(CarrierType.USPS)
                .build();

        doThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY))
                .when(shopifyFulfillmentApiClient).createFulfillment(anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> sender.send(order, invoice))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ─────────────────────────────────────────────────────────
    // sendBulk() — 다건
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendBulk() — FulfillmentTargetDto 목록을 그대로 apiClient.createBulkFulfillment()에 위임한다")
    void sendBulk_delegatesToApiClient() {
        given(channelApiMapper.findShopifyCredential(SELLER_ID)).willReturn(cred());

        List<FulfillmentTargetDto> targets = List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "INV-A", "UPS"),
                buildTarget("ORD-B", "gid://shopify/FulfillmentOrder/2", "INV-B", "USPS")
        );

        sender.sendBulk(SELLER_ID, targets);

        verify(shopifyFulfillmentApiClient).createBulkFulfillment(STORE_NAME, ACCESS_TOKEN, targets);
    }

    @Test
    @DisplayName("sendBulk() — apiClient 예외를 그대로 전파한다")
    void sendBulk_propagatesExceptionFromClient() {
        given(channelApiMapper.findShopifyCredential(SELLER_ID)).willReturn(cred());

        List<FulfillmentTargetDto> targets = List.of(
                buildTarget("ORD-A", "gid://shopify/FulfillmentOrder/1", "INV-A", "USPS")
        );

        doThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY))
                .when(shopifyFulfillmentApiClient).createBulkFulfillment(anyString(), anyString(), any());

        assertThatThrownBy(() -> sender.sendBulk(SELLER_ID, targets))
                .isInstanceOf(HttpClientErrorException.class);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private ShopifyCredentialDto cred() {
        ShopifyCredentialDto dto = new ShopifyCredentialDto();
        dto.setStoreName(STORE_NAME);
        dto.setAccessToken(ACCESS_TOKEN);
        return dto;
    }

    private FulfillmentTargetDto buildTarget(String orderId, String fulfillmentOrderId,
                                             String invoiceNo, String carrierType) {
        FulfillmentTargetDto dto = new FulfillmentTargetDto();
        dto.setOrderId(orderId);
        dto.setFulfillmentOrderId(fulfillmentOrderId);
        dto.setInvoiceNo(invoiceNo);
        dto.setCarrierType(carrierType);
        return dto;
    }
}
