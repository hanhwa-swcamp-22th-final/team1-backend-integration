package com.conk.integration.support;
import com.conk.integration.command.application.service.*;


import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.application.dto.response.ShopifyOrderDto;
import com.conk.integration.command.domain.aggregate.*;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.service.EasyPostApiClient;
import com.conk.integration.command.infrastructure.service.ShopifyApiClient;
import com.conk.integration.command.infrastructure.service.ShopifyFulfillmentApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * [3단계] Command Application Service 단위 테스트 — Mockito
 *
 * - @ExtendWith(MockitoExtension.class) : Spring Context 없이 동작하는 경량 Mockito 환경
 * - Repository / API 클라이언트를 @Mock으로 대체하여 Service 비즈니스 로직 자체에 집중
 *
 * ※ package-private 메서드(selectCheapestRate, resolveCarrierType 등) 접근을 위해
 *   테스트 패키지를 실제 소스와 동일한 패키지 (command.application.service)로 배치
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[Service] Command Application Service 단위 테스트 (Mockito)")
class CommandApplicationServiceTest {

    /* ===================================================================
     * ShopifyOrderSyncService
     * =================================================================== */

    @Nested
    @DisplayName("ShopifyOrderSyncService")
    class ShopifyOrderSyncServiceTests {

        @Mock
        private ShopifyApiClient shopifyApiClient;

        @Mock
        private ChannelOrderRepository channelOrderRepository;

        @InjectMocks
        private ShopifyOrderSyncService shopifyOrderSyncService;

        @Test
        @DisplayName("syncOrders() — 신규 주문만 DB에 저장된다 (이미 존재하는 주문 skip)")
        void syncOrders_savesOnlyNewOrders() {
            // given — 주문 1001: 이미 존재, 주문 1002: 신규
            ShopifyOrderDto existingDto = buildOrderDto(1001L, "#1001");
            ShopifyOrderDto newDto      = buildOrderDto(1002L, "#1002");

            given(shopifyApiClient.getOrders()).willReturn(List.of(existingDto, newDto));
            given(channelOrderRepository.existsById("1001")).willReturn(true);   // 중복
            given(channelOrderRepository.existsById("1002")).willReturn(false);  // 신규

            // when
            shopifyOrderSyncService.syncOrders("seller-A");

            // then — save()는 신규 주문(1002)에 대해서만 1번 호출
            then(channelOrderRepository).should(times(1)).save(any(ChannelOrder.class));
        }

        @Test
        @DisplayName("syncOrders() — 모든 주문이 이미 존재하면 save()가 호출되지 않는다")
        void syncOrders_savesNothing_whenAllExist() {
            // given
            given(shopifyApiClient.getOrders())
                    .willReturn(List.of(buildOrderDto(2001L, "#2001"), buildOrderDto(2002L, "#2002")));
            given(channelOrderRepository.existsById(anyString())).willReturn(true);

            // when
            shopifyOrderSyncService.syncOrders("seller-B");

            // then
            then(channelOrderRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("syncOrders() — Shopify 주문 목록이 0건이면 save()가 호출되지 않는다")
        void syncOrders_savesNothing_whenEmptyOrders() {
            // given
            given(shopifyApiClient.getOrders()).willReturn(List.of());

            // when
            shopifyOrderSyncService.syncOrders("seller-C");

            // then
            then(channelOrderRepository).should(never()).save(any());
        }

        private ShopifyOrderDto buildOrderDto(long id, String name) {
            ShopifyOrderDto dto = new ShopifyOrderDto();
            dto.setId(id);
            dto.setName(name);
            dto.setCreatedAt("2024-01-15T10:00:00+09:00");
            return dto;
        }
    }

    /* ===================================================================
     * EasyPostInvoiceSaveService
     * =================================================================== */

    @Nested
    @DisplayName("EasyPostInvoiceSaveService")
    class EasyPostInvoiceSaveServiceTests {

        @Mock
        private EasyPostApiClient easyPostApiClient;

        @Mock
        private EasypostShipmentInvoiceRepository invoiceRepository;

        @InjectMocks
        private EasyPostInvoiceSaveService easyPostInvoiceSaveService;

        @Test
        @DisplayName("createAndSaveInvoice() — 최저가 rate 선택 후 shipment를 구매하고 DB에 저장한다")
        void createAndSaveInvoice_buysLowestRateAndSaves() {
            // given — createShipment 응답
            EasyPostShipmentResponse.RateDto cheapRate = new EasyPostShipmentResponse.RateDto();
            cheapRate.setId("rate-cheap"); cheapRate.setCarrier("USPS"); cheapRate.setRate("5.00");

            EasyPostShipmentResponse.RateDto expRate = new EasyPostShipmentResponse.RateDto();
            expRate.setId("rate-exp"); expRate.setCarrier("UPS"); expRate.setRate("15.00");

            EasyPostShipmentResponse createResponse = new EasyPostShipmentResponse();
            createResponse.setId("shp-001");
            createResponse.setRates(List.of(expRate, cheapRate)); // 비싼 것 먼저

            // buyRate 응답
            EasyPostShipmentResponse.RateDto selected = new EasyPostShipmentResponse.RateDto();
            selected.setId("rate-cheap"); selected.setCarrier("USPS"); selected.setRate("5.00");

            EasyPostShipmentResponse boughtResponse = new EasyPostShipmentResponse();
            boughtResponse.setId("shp-001");
            boughtResponse.setSelectedRate(selected);

            EasypostShipmentInvoice savedInvoice = EasypostShipmentInvoice.builder()
                    .invoiceNo("shp-001").carrierType(CarrierType.USPS).freightChargeAmt(500).build();

            given(easyPostApiClient.createShipment(any())).willReturn(createResponse);
            given(easyPostApiClient.buyRate("shp-001", "rate-cheap")).willReturn(boughtResponse);
            given(invoiceRepository.save(any())).willReturn(savedInvoice);

            // when
            EasypostShipmentInvoice result = easyPostInvoiceSaveService
                    .createAndSaveInvoice(EasyPostCreateShipmentRequest.builder().build());

            // then
            assertThat(result.getInvoiceNo()).isEqualTo("shp-001");
            then(easyPostApiClient).should(times(1)).createShipment(any());
            then(easyPostApiClient).should(times(1)).buyRate("shp-001", "rate-cheap");
            then(invoiceRepository).should(times(1)).save(any());
        }

        @Test
        @DisplayName("selectCheapestRate() — rates 목록 중 가장 낮은 금액의 rate를 반환한다")
        void selectCheapestRate_returnsLowestRate() {
            // given
            EasyPostShipmentResponse.RateDto r1 = rate("r1", "12.50");
            EasyPostShipmentResponse.RateDto r2 = rate("r2", "7.30");
            EasyPostShipmentResponse.RateDto r3 = rate("r3", "9.99");

            // when — package-private 메서드 직접 호출 (동일 패키지)
            EasyPostShipmentResponse.RateDto cheapest =
                    easyPostInvoiceSaveService.selectCheapestRate(List.of(r1, r2, r3));

            // then
            assertThat(cheapest.getId()).isEqualTo("r2");
            assertThat(cheapest.getRate()).isEqualTo("7.30");
        }

        @Test
        @DisplayName("selectCheapestRate() — rates가 비어있으면 IllegalStateException을 던진다")
        void selectCheapestRate_throwsWhenEmpty() {
            assertThatThrownBy(() -> easyPostInvoiceSaveService.selectCheapestRate(List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No rates available");
        }

        @Test
        @DisplayName("selectCheapestRate() — rate가 null인 항목은 무시되고 유효한 최저가를 반환한다")
        void selectCheapestRate_ignoresNullRates() {
            // given
            EasyPostShipmentResponse.RateDto rNull = new EasyPostShipmentResponse.RateDto();
            rNull.setId("r-null"); // rate(금액) = null

            EasyPostShipmentResponse.RateDto rValid = rate("r-valid", "8.00");

            // when
            EasyPostShipmentResponse.RateDto result =
                    easyPostInvoiceSaveService.selectCheapestRate(List.of(rNull, rValid));

            // then
            assertThat(result.getId()).isEqualTo("r-valid");
        }

        @Test
        @DisplayName("resolveCarrierType() — carrier 문자열에 따라 올바른 CarrierType을 반환한다")
        void resolveCarrierType_mapsCorrectly() {
            assertThat(easyPostInvoiceSaveService.resolveCarrierType("UPS")).isEqualTo(CarrierType.UPS);
            assertThat(easyPostInvoiceSaveService.resolveCarrierType("FEDEX")).isEqualTo(CarrierType.FEDEX);
            assertThat(easyPostInvoiceSaveService.resolveCarrierType("USPS")).isEqualTo(CarrierType.USPS);
            assertThat(easyPostInvoiceSaveService.resolveCarrierType(null)).isEqualTo(CarrierType.USPS);
            assertThat(easyPostInvoiceSaveService.resolveCarrierType("UNKNOWN")).isEqualTo(CarrierType.USPS);
        }

        private EasyPostShipmentResponse.RateDto rate(String id, String rateValue) {
            EasyPostShipmentResponse.RateDto dto = new EasyPostShipmentResponse.RateDto();
            dto.setId(id);
            dto.setRate(rateValue);
            return dto;
        }
    }

    /* ===================================================================
     * ShopifyFulfillmentService
     * =================================================================== */

    @Nested
    @DisplayName("ShopifyFulfillmentService")
    class ShopifyFulfillmentServiceTests {

        @Mock
        private ChannelOrderRepository channelOrderRepository;

        @Mock
        private EasypostShipmentInvoiceRepository invoiceRepository;

        @Mock
        private ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;

        @InjectMocks
        private ShopifyFulfillmentService shopifyFulfillmentService;

        @Test
        @DisplayName("fulfill() — 주문과 invoice가 정상이면 Shopify API를 1회 호출한다")
        void fulfill_callsShopifyApi() {
            // given
            ChannelOrder order = ChannelOrder.builder()
                    .orderId("O-001").channelOrderNo("#1001")
                    .invoiceNo("INV-001").sellerId("S").build();

            EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                    .invoiceNo("INV-001").carrierType(CarrierType.UPS).build();

            given(channelOrderRepository.findById("O-001")).willReturn(Optional.of(order));
            given(invoiceRepository.findById("INV-001")).willReturn(Optional.of(invoice));

            // when
            shopifyFulfillmentService.fulfill("O-001");

            // then
            then(shopifyFulfillmentApiClient).should(times(1))
                    .createFulfillment(eq("#1001"), any());
        }

        @Test
        @DisplayName("fulfill() — 주문이 없으면 IllegalArgumentException을 던진다")
        void fulfill_throwsWhenOrderNotFound() {
            given(channelOrderRepository.findById("O-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> shopifyFulfillmentService.fulfill("O-NONE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ChannelOrder를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("fulfill() — invoiceNo가 null이면 IllegalStateException을 던진다")
        void fulfill_throwsWhenInvoiceNoIsNull() {
            ChannelOrder order = ChannelOrder.builder()
                    .orderId("O-002").channelOrderNo("#1002")
                    .invoiceNo(null).sellerId("S").build();

            given(channelOrderRepository.findById("O-002")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> shopifyFulfillmentService.fulfill("O-002"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("송장이 발급되지 않은 주문");
        }

        @Test
        @DisplayName("fulfill() — invoice가 DB에 없으면 IllegalStateException을 던진다")
        void fulfill_throwsWhenInvoiceNotInDb() {
            ChannelOrder order = ChannelOrder.builder()
                    .orderId("O-003").channelOrderNo("#1003")
                    .invoiceNo("INV-NOT-EXIST").sellerId("S").build();

            given(channelOrderRepository.findById("O-003")).willReturn(Optional.of(order));
            given(invoiceRepository.findById("INV-NOT-EXIST")).willReturn(Optional.empty());

            assertThatThrownBy(() -> shopifyFulfillmentService.fulfill("O-003"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EasypostShipmentInvoice를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("resolveCarrierCompany() — CarrierType에 따라 올바른 운송사 이름을 반환한다")
        void resolveCarrierCompany_mapsCorrectly() {
            // package-private 메서드 직접 호출 (동일 패키지)
            assertThat(shopifyFulfillmentService.resolveCarrierCompany(CarrierType.UPS)).isEqualTo("UPS");
            assertThat(shopifyFulfillmentService.resolveCarrierCompany(CarrierType.FEDEX)).isEqualTo("FedEx");
            assertThat(shopifyFulfillmentService.resolveCarrierCompany(CarrierType.USPS)).isEqualTo("USPS");
            assertThat(shopifyFulfillmentService.resolveCarrierCompany(null)).isEqualTo("USPS");
        }
    }
}
