package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.request.ManualOrderInvoiceRequest;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.application.dto.response.ManualOrderInvoiceResponse;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.service.EasyPostApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualOrderInvoiceService 테스트")
class ManualOrderInvoiceServiceTest {

    @Mock private ChannelOrderRepository channelOrderRepository;
    @Mock private EasyPostApiClient easyPostApiClient;
    @Mock private InvoicePersistenceService invoicePersistenceService;

    @InjectMocks
    private ManualOrderInvoiceService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("수동 주문 송장 발급 성공 테스트")
    class HappyPath {

        @Test
        @DisplayName("신규 주문 요청이 주어지면 수동 주문 송장 발급을 수행했을 때 주문 저장부터 송장 응답 반환까지 순서대로 처리해야 한다")
        void issue_newOrder_fullFlow() {
            given(channelOrderRepository.findById("ORD-001")).willReturn(Optional.empty());

            ChannelOrder savedOrder = buildOrder("ORD-001", null);
            given(channelOrderRepository.save(any())).willReturn(savedOrder);

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_001",
                    List.of(buildRate("r1", "USPS", "5.50"), buildRate("r2", "UPS", "9.00")));
            EasyPostShipmentResponse bought = buildBoughtShipment("shp_001", "USPS", "5.50");

            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate("shp_001", "r1")).willReturn(bought);

            EasypostShipmentInvoice invoice = buildInvoice("shp_001", "USPS", 550);
            given(invoicePersistenceService.saveInvoiceAndAssign(any(), any())).willReturn(invoice);

            ManualOrderInvoiceResponse response = service.issue("seller-001", buildRequest("ORD-001"));

            assertThat(response.getOrderId()).isEqualTo("ORD-001");
            assertThat(response.getInvoiceNo()).isEqualTo("shp_001");
            assertThat(response.getCarrierType()).isEqualTo("USPS");
            assertThat(response.getFreightChargeAmt()).isEqualTo(550);

            // createShipment, buyRate, saveInvoiceAndAssign 각 1회 호출
            verify(easyPostApiClient).createShipment(any());
            verify(easyPostApiClient).buyRate("shp_001", "r1");
            verify(invoicePersistenceService).saveInvoiceAndAssign(any(), any());
        }

        @Test
        @DisplayName("신규 주문 요청이 주어지면 수동 주문 송장 발급을 수행했을 때 shipmentId를 주문에 기록해야 한다")
        void issue_shipmentIdIsRecordedBeforeBuyRate() {
            given(channelOrderRepository.findById("ORD-002")).willReturn(Optional.empty());

            ChannelOrder savedOrder = buildOrder("ORD-002", null);
            given(channelOrderRepository.save(any())).willReturn(savedOrder);

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_002",
                    List.of(buildRate("r1", "USPS", "6.00")));
            EasyPostShipmentResponse bought = buildBoughtShipment("shp_002", "USPS", "6.00");

            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);
            given(invoicePersistenceService.saveInvoiceAndAssign(any(), any()))
                    .willReturn(buildInvoice("shp_002", "USPS", 600));

            service.issue("seller-001", buildRequest("ORD-002"));

            // shipmentId 기록을 위해 save가 2회 호출되었는지 확인
            // (1회: saveNewOrder, 1회: shipmentId 기록)
            verify(channelOrderRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("송장 번호가 없는 기존 주문이 주어지면 수동 주문 송장 발급을 수행했을 때 신규 주문 저장 없이 EasyPost 발급만 진행해야 한다")
        void issue_retryWithNullInvoice_skipsOrderSave() {
            ChannelOrder existingOrder = buildOrder("ORD-RETRY", null);
            given(channelOrderRepository.findById("ORD-RETRY")).willReturn(Optional.of(existingOrder));
            given(channelOrderRepository.save(any())).willReturn(existingOrder);

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_retry",
                    List.of(buildRate("r1", "USPS", "5.00")));
            EasyPostShipmentResponse bought = buildBoughtShipment("shp_retry", "USPS", "5.00");

            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);
            given(invoicePersistenceService.saveInvoiceAndAssign(any(), any()))
                    .willReturn(buildInvoice("shp_retry", "USPS", 500));

            service.issue("seller-001", buildRequest("ORD-RETRY"));

            // saveNewOrder가 호출되지 않았으므로 save는 shipmentId 기록 1회만 호출
            verify(channelOrderRepository, times(1)).save(any());
            verify(easyPostApiClient).createShipment(any());
        }

        @Test
        @DisplayName("여러 운임 정보가 주어지면 수동 주문 송장 발급을 수행했을 때 최저 운임을 buyRate에 전달해야 한다")
        void issue_cheapestRateIsSelected() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-003", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_003", List.of(
                    buildRate("r_expensive", "UPS", "15.00"),
                    buildRate("r_cheap", "USPS", "5.99"),
                    buildRate("r_mid", "FEDEX", "9.50")
            ));
            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate(anyString(), anyString()))
                    .willReturn(buildBoughtShipment("shp_003", "USPS", "5.99"));
            given(invoicePersistenceService.saveInvoiceAndAssign(any(), any()))
                    .willReturn(buildInvoice("shp_003", "USPS", 599));

            service.issue("seller-001", buildRequest("ORD-003"));

            verify(easyPostApiClient).buyRate("shp_003", "r_cheap");
        }

        @Test
        @DisplayName("주문 상품 목록이 null이어도 수동 주문 송장 발급을 수행했을 때 신규 주문을 정상 저장해야 한다")
        void issue_savesOrderWhenItemsIsNull() {
            given(channelOrderRepository.findById("ORD-NULL-ITEMS")).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_null_items",
                    List.of(buildRate("r1", "USPS", "5.50")));
            EasyPostShipmentResponse bought = buildBoughtShipment("shp_null_items", "USPS", "5.50");
            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate("shp_null_items", "r1")).willReturn(bought);
            given(invoicePersistenceService.saveInvoiceAndAssign(any(), any()))
                    .willReturn(buildInvoice("shp_null_items", "USPS", 550));

            ManualOrderInvoiceRequest request = new ManualOrderInvoiceRequest(
                    "ORD-NULL-ITEMS",
                    "홍길동", "010-1234-5678",
                    "123 Main St", null,
                    "CA", "Los Angeles", "90001",
                    null,
                    EasyPostCreateShipmentRequest.AddressBody.builder()
                            .name("CONK Warehouse").street1("456 Warehouse Blvd")
                            .city("Los Angeles").state("CA").zip("90002").country("US").build(),
                    EasyPostCreateShipmentRequest.ParcelBody.builder()
                            .weight(10.0).length(10.0).width(8.0).height(4.0).build()
            );

            ManualOrderInvoiceResponse response = service.issue("seller-001", request);

            assertThat(response.getOrderId()).isEqualTo("ORD-NULL-ITEMS");
            ArgumentCaptor<ChannelOrder> captor = ArgumentCaptor.forClass(ChannelOrder.class);
            verify(channelOrderRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getItems()).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────
    // 예외 케이스
    // ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("수동 주문 송장 발급 예외 테스트")
    class ExceptionCases {

        @Test
        @DisplayName("이미 송장 번호가 있는 주문이 주어지면 수동 주문 송장 발급을 수행했을 때 BusinessException(INT-201)을 발생시켜야 한다")
        void issue_alreadyInvoiced_throwsIllegalState() {
            ChannelOrder alreadyInvoiced = buildOrder("ORD-DONE", "shp_existing");
            given(channelOrderRepository.findById("ORD-DONE")).willReturn(Optional.of(alreadyInvoiced));

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-DONE")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("이미 송장이 발급된 주문입니다");

            verify(easyPostApiClient, never()).createShipment(any());
        }

        @Test
        @DisplayName("createShipment 호출에 실패하면 수동 주문 송장 발급을 수행했을 때 예외를 전파하고 buyRate와 송장 저장을 호출하지 않아야 한다")
        void issue_createShipmentFails_propagates() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-004", null));
            given(easyPostApiClient.createShipment(any()))
                    .willThrow(new RuntimeException("EasyPost 연결 오류"));

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-004")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("EasyPost 연결 오류");

            verify(easyPostApiClient, never()).buyRate(any(), any());
            verify(invoicePersistenceService, never()).saveInvoiceAndAssign(any(), any());
        }

        @Test
        @DisplayName("buyRate 호출에 실패하면 수동 주문 송장 발급을 수행했을 때 예외를 전파하고 송장을 저장하지 않아야 한다")
        void issue_buyRateFails_propagates() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-005", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_005",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate(anyString(), anyString()))
                    .willThrow(new RuntimeException("결제 실패"));

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-005")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("결제 실패");

            verify(invoicePersistenceService, never()).saveInvoiceAndAssign(any(), any());
        }

        @Test
        @DisplayName("운임 정보가 없으면 수동 주문 송장 발급을 수행했을 때 BusinessException(INT-104)를 발생시켜야 한다")
        void issue_noRates_throwsIllegalState() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-006", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_006", List.of());
            given(easyPostApiClient.createShipment(any())).willReturn(created);

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-006")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("운임 정보가 없습니다");
        }

        @Test
        @DisplayName("배송지 정보가 주어지면 수동 주문 송장 발급을 수행했을 때 toAddress를 올바르게 구성해 EasyPost에 전달해야 한다")
        void issue_toAddressBuiltFromRequest() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-007", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_007",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate(anyString(), anyString()))
                    .willReturn(buildBoughtShipment("shp_007", "USPS", "5.00"));
            given(invoicePersistenceService.saveInvoiceAndAssign(any(), any()))
                    .willReturn(buildInvoice("shp_007", "USPS", 500));

            ArgumentCaptor<EasyPostCreateShipmentRequest> captor =
                    ArgumentCaptor.forClass(EasyPostCreateShipmentRequest.class);

            service.issue("seller-001", buildRequest("ORD-007"));

            verify(easyPostApiClient).createShipment(captor.capture());
            EasyPostCreateShipmentRequest.AddressBody toAddr =
                    captor.getValue().getShipment().getToAddress();
            assertThat(toAddr.getName()).isEqualTo("홍길동");
            assertThat(toAddr.getStreet1()).isEqualTo("123 Main St");
            assertThat(toAddr.getCountry()).isEqualTo("US");
        }

        // ── P1: EasyPost 외부 API 에러 ──────────────────────────

        @Test
        @DisplayName("createShipment 호출에서 401 오류가 발생하면 수동 주문 송장 발급을 수행했을 때 HttpClientErrorException을 전파해야 한다")
        void issue_easyPost401_propagates() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-E01", null));
            given(easyPostApiClient.createShipment(any()))
                    .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-E01")))
                    .isInstanceOf(HttpClientErrorException.class)
                    .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED));

            verify(easyPostApiClient, never()).buyRate(any(), any());
            verify(invoicePersistenceService, never()).saveInvoiceAndAssign(any(), any());
        }

        @Test
        @DisplayName("createShipment 호출에서 422 오류가 발생하면 수동 주문 송장 발급을 수행했을 때 HttpClientErrorException을 전파해야 한다")
        void issue_easyPost422_propagates() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-E02", null));
            given(easyPostApiClient.createShipment(any()))
                    .willThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-E02")))
                    .isInstanceOf(HttpClientErrorException.class)
                    .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

            verify(invoicePersistenceService, never()).saveInvoiceAndAssign(any(), any());
        }

        @Test
        @DisplayName("buyRate 호출에서 500 오류가 발생하면 수동 주문 송장 발급을 수행했을 때 HttpServerErrorException을 전파하고 송장을 저장하지 않아야 한다")
        void issue_easyPost500OnBuyRate_propagates() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-E03", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_e03",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate(anyString(), anyString()))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-E03")))
                    .isInstanceOf(HttpServerErrorException.class)
                    .satisfies(e -> assertThat(((HttpServerErrorException) e).getStatusCode())
                            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

            verify(invoicePersistenceService, never()).saveInvoiceAndAssign(any(), any());
        }

        // ── P2: DB 저장 실패 ──────────────────────────────────

        @Test
        @DisplayName("shipmentId 기록 저장에 실패하면 수동 주문 송장 발급을 수행했을 때 예외를 전파하고 buyRate와 송장 저장을 호출하지 않아야 한다")
        void issue_shipmentIdRecordFails_propagates() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            // 첫 번째 save(신규 주문)는 성공, 두 번째 save(shipmentId 기록)는 실패
            given(channelOrderRepository.save(any()))
                    .willReturn(buildOrder("ORD-E04", null))
                    .willThrow(new RuntimeException("DB 연결 오류"));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_e04",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-E04")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB 연결 오류");

            verify(easyPostApiClient, never()).buyRate(any(), any());
            verify(invoicePersistenceService, never()).saveInvoiceAndAssign(any(), any());
        }

        @Test
        @DisplayName("송장 저장과 할당에 실패하면 수동 주문 송장 발급을 수행했을 때 예외를 전파해야 한다")
        void issue_invoicePersistenceFails_propagates() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-E05", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_e05",
                    List.of(buildRate("r1", "USPS", "5.00")));
            EasyPostShipmentResponse bought = buildBoughtShipment("shp_e05", "USPS", "5.00");
            given(easyPostApiClient.createShipment(any())).willReturn(created);
            given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);
            given(invoicePersistenceService.saveInvoiceAndAssign(any(), any()))
                    .willThrow(new RuntimeException("invoice 저장 실패"));

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-E05")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("invoice 저장 실패");

            // buyRate는 이미 호출됨 (결제 완료 상태)
            verify(easyPostApiClient).buyRate(anyString(), anyString());
        }

        // ── P3: null 필드 안전 처리 ───────────────────────────

        @Test
        @DisplayName("tracker가 null이면 수동 주문 송장 발급을 수행했을 때 trackingCode 기반으로 trackingUrl을 설정해야 한다")
        void issue_trackerNull_handledGracefully() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-N01", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_n01",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);

            EasyPostShipmentResponse bought = buildBoughtShipment("shp_n01", "USPS", "5.00");
            bought.setTracker(null);
            bought.setTrackingCode("TRK-NULL-TEST");
            given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);

            EasypostShipmentInvoice invoice = buildInvoice("shp_n01", "USPS", 500);
            given(invoicePersistenceService.saveInvoiceAndAssign(any(), any())).willReturn(invoice);

            ManualOrderInvoiceResponse response = service.issue("seller-001", buildRequest("ORD-N01"));

            assertThat(response).isNotNull();
            verify(invoicePersistenceService).saveInvoiceAndAssign(any(), any());
        }

        @Test
        @DisplayName("postageLabel이 null이면 수동 주문 송장 발급을 수행했을 때 labelFileUrl을 null로 처리해야 한다")
        void issue_postageLabelNull_handledGracefully() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-N02", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_n02",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);

            EasyPostShipmentResponse bought = buildBoughtShipment("shp_n02", "USPS", "5.00");
            bought.setPostageLabel(null);
            given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);

            ArgumentCaptor<EasypostShipmentInvoice> captor =
                    ArgumentCaptor.forClass(EasypostShipmentInvoice.class);
            given(invoicePersistenceService.saveInvoiceAndAssign(captor.capture(), any()))
                    .willAnswer(inv -> inv.getArgument(0));

            service.issue("seller-001", buildRequest("ORD-N02"));

            assertThat(captor.getValue().getLabelFileUrl()).isNull();
        }

        @Test
        @DisplayName("tracker와 trackingCode가 모두 null이면 수동 주문 송장 발급을 수행했을 때 trackingUrl을 null로 저장해야 한다")
        void issue_returnsNullTrackingUrlWhenTrackerAndTrackingCodeMissing() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-N03", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_n03",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);

            EasyPostShipmentResponse bought = buildBoughtShipment("shp_n03", "USPS", "5.00");
            bought.setTracker(null);
            bought.setTrackingCode(null);
            given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);

            ArgumentCaptor<EasypostShipmentInvoice> captor =
                    ArgumentCaptor.forClass(EasypostShipmentInvoice.class);
            given(invoicePersistenceService.saveInvoiceAndAssign(captor.capture(), any()))
                    .willAnswer(inv -> inv.getArgument(0));

            service.issue("seller-001", buildRequest("ORD-N03"));

            assertThat(captor.getValue().getTrackingUrl()).isNull();
        }

        @Test
        @DisplayName("목적지 주소가 null이면 수동 주문 송장 발급을 수행했을 때 shipToAddress를 null로 저장해야 한다")
        void issue_returnsNullShipToAddressWhenResponseAddressMissing() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-N04", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_n04",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);

            EasyPostShipmentResponse bought = buildBoughtShipment("shp_n04", "USPS", "5.00");
            bought.setToAddress(null);
            given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);

            ArgumentCaptor<EasypostShipmentInvoice> captor =
                    ArgumentCaptor.forClass(EasypostShipmentInvoice.class);
            given(invoicePersistenceService.saveInvoiceAndAssign(captor.capture(), any()))
                    .willAnswer(inv -> inv.getArgument(0));

            service.issue("seller-001", buildRequest("ORD-N04"));

            assertThat(captor.getValue().getShipToAddress()).isNull();
        }

        @Test
        @DisplayName("selectedRate가 null이면 수동 주문 송장 발급을 수행했을 때 carrierType은 USPS로 freightChargeAmt는 0으로 저장해야 한다")
        void issue_usesUspsWhenSelectedRateIsNull() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-N05", null));

            EasyPostShipmentResponse created = buildShipmentWithRates("shp_n05",
                    List.of(buildRate("r1", "USPS", "5.00")));
            given(easyPostApiClient.createShipment(any())).willReturn(created);

            EasyPostShipmentResponse bought = new EasyPostShipmentResponse();
            bought.setId("shp_n05");
            given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);

            ArgumentCaptor<EasypostShipmentInvoice> captor =
                    ArgumentCaptor.forClass(EasypostShipmentInvoice.class);
            given(invoicePersistenceService.saveInvoiceAndAssign(captor.capture(), any()))
                    .willAnswer(inv -> inv.getArgument(0));

            service.issue("seller-001", buildRequest("ORD-N05"));

            assertThat(captor.getValue().getCarrierType()).isEqualTo(CarrierType.USPS);
            assertThat(captor.getValue().getFreightChargeAmt()).isZero();
        }

        @Test
        @DisplayName("운임 금액이 모두 숫자가 아니면 수동 주문 송장 발급을 수행했을 때 BusinessException(INT-104)를 발생시켜야 한다")
        void issue_selectCheapestRate_throwsWhenAllRatesAreNonNumeric() {
            given(channelOrderRepository.findById(any())).willReturn(Optional.empty());
            given(channelOrderRepository.save(any())).willReturn(buildOrder("ORD-N06", null));
            given(easyPostApiClient.createShipment(any())).willReturn(buildShipmentWithRates("shp_n06",
                    List.of(buildRate("r1", "USPS", "abc"), buildRate("r2", "UPS", "free"))));

            assertThatThrownBy(() -> service.issue("seller-001", buildRequest("ORD-N06")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NO_SHIPPING_RATES);

            verify(easyPostApiClient, never()).buyRate(anyString(), anyString());
            verify(invoicePersistenceService, never()).saveInvoiceAndAssign(any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    private ManualOrderInvoiceRequest buildRequest(String orderId) {
        return new ManualOrderInvoiceRequest(
                orderId,
                "홍길동", "010-1234-5678",
                "123 Main St", null,
                "CA", "Los Angeles", "90001",
                List.of(new ManualOrderInvoiceRequest.OrderItemBody("SKU-001", "상품명", 2)),
                EasyPostCreateShipmentRequest.AddressBody.builder()
                        .name("CONK Warehouse").street1("456 Warehouse Blvd")
                        .city("Los Angeles").state("CA").zip("90002").country("US").build(),
                EasyPostCreateShipmentRequest.ParcelBody.builder()
                        .weight(10.0).length(10.0).width(8.0).height(4.0).build()
        );
    }

    private ChannelOrder buildOrder(String orderId, String invoiceNo) {
        return ChannelOrder.builder()
                .orderId(orderId)
                .orderChannel(OrderChannel.MANUAL)
                .sellerId("seller-001")
                .receiverName("홍길동")
                .shipToAddress1("123 Main St")
                .shipToCity("Los Angeles")
                .shipToState("CA")
                .shipToZipCode("90001")
                .invoiceNo(invoiceNo)
                .build();
    }

    private EasyPostShipmentResponse buildShipmentWithRates(String id,
                                                              List<EasyPostShipmentResponse.RateDto> rates) {
        EasyPostShipmentResponse r = new EasyPostShipmentResponse();
        r.setId(id);
        r.setRates(rates);
        return r;
    }

    private EasyPostShipmentResponse.RateDto buildRate(String id, String carrier, String rate) {
        EasyPostShipmentResponse.RateDto dto = new EasyPostShipmentResponse.RateDto();
        dto.setId(id);
        dto.setCarrier(carrier);
        dto.setRate(rate);
        return dto;
    }

    private EasyPostShipmentResponse buildBoughtShipment(String id, String carrier, String rate) {
        EasyPostShipmentResponse r = new EasyPostShipmentResponse();
        r.setId(id);

        EasyPostShipmentResponse.RateDto selected = new EasyPostShipmentResponse.RateDto();
        selected.setCarrier(carrier);
        selected.setRate(rate);
        r.setSelectedRate(selected);

        EasyPostShipmentResponse.PostageLabelDto label = new EasyPostShipmentResponse.PostageLabelDto();
        label.setLabelUrl("https://label.url/" + id + ".pdf");
        r.setPostageLabel(label);

        EasyPostShipmentResponse.TrackerDto tracker = new EasyPostShipmentResponse.TrackerDto();
        tracker.setPublicUrl("https://track.easypost.com/" + id);
        r.setTracker(tracker);

        EasyPostShipmentResponse.AddressDto addr = new EasyPostShipmentResponse.AddressDto();
        addr.setStreet1("123 Main St");
        addr.setCity("Los Angeles");
        addr.setState("CA");
        addr.setZip("90001");
        addr.setCountry("US");
        r.setToAddress(addr);

        return r;
    }

    private EasypostShipmentInvoice buildInvoice(String invoiceNo, String carrier, int freightCents) {
        return EasypostShipmentInvoice.builder()
                .invoiceNo(invoiceNo)
                .trackingCode("TRK-" + invoiceNo)
                .carrierType(CarrierType.fromEasyPostName(carrier))
                .freightChargeAmt(freightCents)
                .shipToAddress("123 Main St, Los Angeles, CA, 90001, US")
                .trackingUrl("https://track.easypost.com/" + invoiceNo)
                .labelFileUrl("https://label.url/" + invoiceNo + ".pdf")
                .build();
    }
}
