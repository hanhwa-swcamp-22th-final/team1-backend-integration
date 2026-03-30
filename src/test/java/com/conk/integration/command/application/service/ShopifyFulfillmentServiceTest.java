package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.CarrierType;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.service.ShopifyFulfillmentApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyFulfillmentService 단위 테스트")
class ShopifyFulfillmentServiceTest {

    @Mock private ChannelOrderRepository channelOrderRepository;
    @Mock private EasypostShipmentInvoiceRepository invoiceRepository;
    @Mock private ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;

    @InjectMocks
    private ShopifyFulfillmentService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] 정상 플로우 - ChannelOrder 조회 → Invoice 조회 → fulfillment 호출")
    void fulfill_happyPath() {
        ChannelOrder order = buildChannelOrder("ORD-001", "shp_invoice_001", "5678901234");
        EasypostShipmentInvoice invoice = buildInvoice("shp_invoice_001", CarrierType.UPS, "1Z999AA10123456784");

        given(channelOrderRepository.findById("ORD-001")).willReturn(Optional.of(order));
        given(invoiceRepository.findById("shp_invoice_001")).willReturn(Optional.of(invoice));

        service.fulfill("ORD-001");

        verify(channelOrderRepository).findById("ORD-001");
        verify(invoiceRepository).findById("shp_invoice_001");
        verify(shopifyFulfillmentApiClient).createFulfillment(eq("5678901234"), any());
    }

    @Test
    @DisplayName("[GREEN] CarrierType.UPS → carrier company 'UPS' 매핑")
    void resolveCarrierCompany_ups() {
        assertThat(service.resolveCarrierCompany(CarrierType.UPS)).isEqualTo("UPS");
    }

    @Test
    @DisplayName("[GREEN] CarrierType.FEDEX → carrier company 'FedEx' 매핑")
    void resolveCarrierCompany_fedex() {
        assertThat(service.resolveCarrierCompany(CarrierType.FEDEX)).isEqualTo("FedEx");
    }

    @Test
    @DisplayName("[GREEN] CarrierType.USPS → carrier company 'USPS' 매핑")
    void resolveCarrierCompany_usps() {
        assertThat(service.resolveCarrierCompany(CarrierType.USPS)).isEqualTo("USPS");
    }

    // ─────────────────────────────────────────────────────────
    // 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] ChannelOrder 없음 → IllegalArgumentException, fulfillment 미호출")
    void fulfill_throwsWhenOrderNotFound() {
        given(channelOrderRepository.findById(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.fulfill("NOT_EXIST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_EXIST");

        verify(shopifyFulfillmentApiClient, never()).createFulfillment(any(), any());
    }

    @Test
    @DisplayName("[예외] invoiceNo null → IllegalStateException (송장 미발급), fulfillment 미호출")
    void fulfill_throwsWhenInvoiceNoIsNull() {
        ChannelOrder orderWithoutInvoice = buildChannelOrder("ORD-002", null, "5678901234");
        given(channelOrderRepository.findById("ORD-002")).willReturn(Optional.of(orderWithoutInvoice));

        assertThatThrownBy(() -> service.fulfill("ORD-002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("송장");

        verify(shopifyFulfillmentApiClient, never()).createFulfillment(any(), any());
    }

    @Test
    @DisplayName("[예외] EasypostShipmentInvoice 없음 → IllegalStateException, fulfillment 미호출")
    void fulfill_throwsWhenInvoiceNotFound() {
        ChannelOrder order = buildChannelOrder("ORD-003", "shp_missing", "5678901234");
        given(channelOrderRepository.findById("ORD-003")).willReturn(Optional.of(order));
        given(invoiceRepository.findById("shp_missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.fulfill("ORD-003"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shp_missing");

        verify(shopifyFulfillmentApiClient, never()).createFulfillment(any(), any());
    }

    @Test
    @DisplayName("[예외] ShopifyFulfillmentApiClient 실패 → 예외 전파")
    void fulfill_propagatesExceptionFromClient() {
        ChannelOrder order = buildChannelOrder("ORD-004", "shp_invoice_004", "5678901234");
        EasypostShipmentInvoice invoice = buildInvoice("shp_invoice_004", CarrierType.USPS, "9400111899223397888692");

        given(channelOrderRepository.findById("ORD-004")).willReturn(Optional.of(order));
        given(invoiceRepository.findById("shp_invoice_004")).willReturn(Optional.of(invoice));
        given(shopifyFulfillmentApiClient.createFulfillment(anyString(), any()))
                .willThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> service.fulfill("ORD-004"))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private ChannelOrder buildChannelOrder(String orderId, String invoiceNo, String channelOrderNo) {
        return ChannelOrder.builder()
                .orderId(orderId)
                .channelOrderNo(channelOrderNo)
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId("seller-001")
                .invoiceNo(invoiceNo)
                .build();
    }

    private EasypostShipmentInvoice buildInvoice(String invoiceNo, CarrierType carrierType, String trackingUrl) {
        return EasypostShipmentInvoice.builder()
                .invoiceNo(invoiceNo)
                .carrierType(carrierType)
                .trackingUrl(trackingUrl)
                .freightChargeAmt(640)
                .build();
    }
}
