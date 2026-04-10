package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.request.OrderInvoicePair;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.command.application.dto.response.BulkInvoiceResponse;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.infrastructure.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.mapper.ChannelOrderCommandMapper;
import com.conk.integration.command.infrastructure.service.EasyPostApiClient;
import com.conk.integration.query.dto.InvoiceTargetDto;
import com.conk.integration.query.mapper.ChannelOrderInvoiceMapper;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

// EasyPost 송장 저장 서비스의 정상 흐름과 예외 전파를 Mockito로 검증한다.
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyPostInvoiceSaveService 테스트")
class EasyPostInvoiceSaveServiceTest {

    @Mock private EasyPostApiClient easyPostApiClient;
    @Mock private EasypostShipmentInvoiceRepository invoiceRepository;
    @Mock private ChannelOrderCommandMapper channelOrderCommandMapper;
    @Mock private ChannelOrderInvoiceMapper channelOrderInvoiceMapper;

    @InjectMocks
    private EasyPostInvoiceSaveService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 운송장 요청이 주어지면 송장을 생성하고 저장했을 때 생성, 구매, 저장 순서로 처리해야 한다")
    void createAndSaveInvoice_fullHappyPath() {
        // 비싼/싼 운임을 함께 내려 최저가 선택과 저장을 한 번에 확인한다.
        EasyPostShipmentResponse created = buildShipmentWithRates("shp_001",
                List.of(buildRate("r1", "USPS", "6.40"), buildRate("r2", "UPS", "10.50")));
        EasyPostShipmentResponse bought = buildBoughtShipment("shp_001", "USPS", "6.40",
                "https://label.url/abc.pdf", "https://track.easypost.com/abc");

        given(easyPostApiClient.createShipment(any())).willReturn(created);
        given(easyPostApiClient.buyRate("shp_001", "r1")).willReturn(bought);
        given(invoiceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        EasypostShipmentInvoice result = service.createAndSaveInvoice(buildRequest());

        assertThat(result).isNotNull();
        verify(easyPostApiClient).createShipment(any());
        verify(easyPostApiClient).buyRate("shp_001", "r1");
        verify(invoiceRepository).save(any());
    }

    @Test
    @DisplayName("여러 운임 정보가 주어지면 최저 운임을 선택했을 때 가장 저렴한 운임을 반환해야 한다")
    void selectCheapestRate_picksLowestRate() {
        // 입력 순서와 상관없이 최저 운임이 선택되어야 한다.
        List<EasyPostShipmentResponse.RateDto> rates = List.of(
                buildRate("r_expensive", "UPS", "15.00"),
                buildRate("r_cheap", "USPS", "5.99"),
                buildRate("r_mid", "FEDEX", "9.50")
        );

        EasyPostShipmentResponse.RateDto cheapest = service.selectCheapestRate(rates);

        assertThat(cheapest.getId()).isEqualTo("r_cheap");
        assertThat(cheapest.getRate()).isEqualTo("5.99");
    }

    @Test
    @DisplayName("FedEx 운송사 정보가 주어지면 송장을 생성하고 저장했을 때 FEDEX enum으로 매핑해야 한다")
    void fromEasyPostName_fedex() {
        assertThat(CarrierType.fromEasyPostName("FedEx")).isEqualTo(CarrierType.FEDEX);
        assertThat(CarrierType.fromEasyPostName("FEDEX")).isEqualTo(CarrierType.FEDEX);
    }

    @Test
    @DisplayName("UPS 운송사 정보가 주어지면 송장을 생성하고 저장했을 때 UPS enum으로 매핑해야 한다")
    void fromEasyPostName_ups() {
        assertThat(CarrierType.fromEasyPostName("UPS")).isEqualTo(CarrierType.UPS);
    }

    @Test
    @DisplayName("알 수 없는 운송사 정보가 주어지면 송장을 생성하고 저장했을 때 USPS enum으로 매핑해야 한다")
    void fromEasyPostName_unknown() {
        assertThat(CarrierType.fromEasyPostName("DHL")).isEqualTo(CarrierType.USPS);
        assertThat(CarrierType.fromEasyPostName(null)).isEqualTo(CarrierType.USPS);
    }

    @Test
    @DisplayName("송장 응답 정보가 주어지면 송장을 생성하고 저장했을 때 주요 필드를 정확히 매핑해야 한다")
    void createAndSaveInvoice_mapsFieldsCorrectly() {
        // 외부 응답이 엔티티 필드로 어떻게 변환되는지 캡처해서 본다.
        EasyPostShipmentResponse created = buildShipmentWithRates("shp_field_test",
                List.of(buildRate("r1", "USPS", "6.40")));
        EasyPostShipmentResponse bought = buildBoughtShipment("shp_field_test", "USPS", "6.40",
                "https://label.url/label.pdf", "https://track.easypost.com/trk123");

        given(easyPostApiClient.createShipment(any())).willReturn(created);
        given(easyPostApiClient.buyRate(anyString(), anyString())).willReturn(bought);
        given(invoiceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<EasypostShipmentInvoice> captor = ArgumentCaptor.forClass(EasypostShipmentInvoice.class);

        service.createAndSaveInvoice(buildRequest());

        verify(invoiceRepository).save(captor.capture());
        EasypostShipmentInvoice saved = captor.getValue();
        assertThat(saved.getInvoiceNo()).isEqualTo("shp_field_test");
        assertThat(saved.getCarrierType()).isEqualTo(CarrierType.USPS);
        assertThat(saved.getFreightChargeAmt()).isEqualTo(640);  // $6.40 → 640 cents
        assertThat(saved.getLabelFileUrl()).isEqualTo("https://label.url/label.pdf");
        assertThat(saved.getTrackingUrl()).isEqualTo("https://track.easypost.com/trk123");
    }

    // ─────────────────────────────────────────────────────────
    // selectCheapestRate 예외
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("운임 정보가 null이면 최저 운임을 선택했을 때 BusinessException(INT-104)를 발생시켜야 한다")
    void selectCheapestRate_throwsWhenNull() {
        assertThatThrownBy(() -> service.selectCheapestRate(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("운임 정보가 없습니다");
    }

    @Test
    @DisplayName("운임 정보가 빈 목록이면 최저 운임을 선택했을 때 BusinessException(INT-104)를 발생시켜야 한다")
    void selectCheapestRate_throwsWhenEmpty() {
        assertThatThrownBy(() -> service.selectCheapestRate(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("운임 정보가 없습니다");
    }

    // ─────────────────────────────────────────────────────────
    // 예외 전파
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createShipment 호출에서 401 오류가 발생하면 송장을 생성하고 저장했을 때 예외를 전파하고 저장하지 않아야 한다")
    void createAndSaveInvoice_propagates_whenUnauthorized() {
        given(easyPostApiClient.createShipment(any()))
                .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.createAndSaveInvoice(buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("buyRate 호출에서 500 오류가 발생하면 송장을 생성하고 저장했을 때 예외를 전파하고 저장하지 않아야 한다")
    void createAndSaveInvoice_propagates_whenBuyRateServerError() {
        EasyPostShipmentResponse created = buildShipmentWithRates("shp_001",
                List.of(buildRate("r1", "USPS", "6.40")));
        given(easyPostApiClient.createShipment(any())).willReturn(created);
        given(easyPostApiClient.buyRate(anyString(), anyString()))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.createAndSaveInvoice(buildRequest()))
                .isInstanceOf(HttpServerErrorException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("createShipment 응답에 운임 정보가 없으면 송장을 생성하고 저장했을 때 BusinessException(INT-104)를 발생시켜야 한다")
    void createAndSaveInvoice_throwsWhenNoRates() {
        // 구매 가능한 rate가 없으면 저장 전에 즉시 실패해야 한다.
        EasyPostShipmentResponse created = buildShipmentWithRates("shp_001", List.of());
        given(easyPostApiClient.createShipment(any())).willReturn(created);

        assertThatThrownBy(() -> service.createAndSaveInvoice(buildRequest()))
                .isInstanceOf(BusinessException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("selectedRate가 null이면 송장을 생성하고 저장했을 때 carrierType은 USPS로 freightChargeAmt는 0으로 저장해야 한다")
    void createAndSaveInvoice_usesUspsWhenSelectedRateIsNull() {
        EasyPostShipmentResponse created = buildShipmentWithRates("shp_null_rate",
                List.of(buildRate("r1", "USPS", "5.50")));
        EasyPostShipmentResponse bought = new EasyPostShipmentResponse();
        bought.setId("shp_null_rate");

        given(easyPostApiClient.createShipment(any())).willReturn(created);
        given(easyPostApiClient.buyRate("shp_null_rate", "r1")).willReturn(bought);
        given(invoiceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        EasypostShipmentInvoice result = service.createAndSaveInvoice(buildRequest());

        assertThat(result.getCarrierType()).isEqualTo(CarrierType.USPS);
        assertThat(result.getFreightChargeAmt()).isZero();
    }

    @Test
    @DisplayName("tracker와 trackingCode가 모두 없으면 송장을 생성하고 저장했을 때 trackingUrl을 null로 저장해야 한다")
    void createAndSaveInvoice_returnsNullTrackingUrlWhenTrackerAndTrackingCodeMissing() {
        EasyPostShipmentResponse created = buildShipmentWithRates("shp_no_track",
                List.of(buildRate("r1", "USPS", "5.50")));
        EasyPostShipmentResponse bought = buildBoughtShipment("shp_no_track", "USPS", "5.50",
                "https://label.url/no-track.pdf", null);
        bought.setTracker(null);
        bought.setTrackingCode(null);

        given(easyPostApiClient.createShipment(any())).willReturn(created);
        given(easyPostApiClient.buyRate("shp_no_track", "r1")).willReturn(bought);
        given(invoiceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        EasypostShipmentInvoice result = service.createAndSaveInvoice(buildRequest());

        assertThat(result.getTrackingUrl()).isNull();
    }

    @Test
    @DisplayName("목적지 주소가 없으면 송장을 생성하고 저장했을 때 shipToAddress를 null로 저장해야 한다")
    void createAndSaveInvoice_returnsNullShipToAddressWhenToAddressMissing() {
        EasyPostShipmentResponse created = buildShipmentWithRates("shp_no_addr",
                List.of(buildRate("r1", "USPS", "5.50")));
        EasyPostShipmentResponse bought = buildBoughtShipment("shp_no_addr", "USPS", "5.50",
                "https://label.url/no-addr.pdf", "https://track.easypost.com/no-addr");
        bought.setToAddress(null);

        given(easyPostApiClient.createShipment(any())).willReturn(created);
        given(easyPostApiClient.buyRate("shp_no_addr", "r1")).willReturn(bought);
        given(invoiceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        EasypostShipmentInvoice result = service.createAndSaveInvoice(buildRequest());

        assertThat(result.getShipToAddress()).isNull();
    }

    @Test
    @DisplayName("운임 금액이 모두 숫자가 아니면 최저 운임을 선택했을 때 BusinessException(INT-104)를 발생시켜야 한다")
    void selectCheapestRate_throwsWhenAllRatesAreNonNumeric() {
        List<EasyPostShipmentResponse.RateDto> rates = List.of(
                buildRate("r1", "USPS", "abc"),
                buildRate("r2", "UPS", "free")
        );

        assertThatThrownBy(() -> service.selectCheapestRate(rates))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("운임 정보가 없습니다");
    }

    // ─────────────────────────────────────────────────────────
    // createAndSaveBulkInvoices()
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("미발급 주문 2건이 주어지면 일괄 송장을 생성했을 때 EasyPost를 두 번 호출하고 송장을 일괄 반영해야 한다")
    void createAndSaveBulkInvoices_fullHappyPath() {
        List<InvoiceTargetDto> targets = List.of(
                buildTarget("ORD-BULK-001"), buildTarget("ORD-BULK-002"));
        given(channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-001")).willReturn(targets);

        EasyPostShipmentResponse created1 = buildShipmentWithRates("shp_bulk_001",
                List.of(buildRate("r1", "USPS", "5.50")));
        EasyPostShipmentResponse bought1 = buildBoughtShipment("shp_bulk_001", "USPS", "5.50",
                "https://label.url/001.pdf", "https://track.easypost.com/001");
        EasyPostShipmentResponse created2 = buildShipmentWithRates("shp_bulk_002",
                List.of(buildRate("r2", "USPS", "6.00")));
        EasyPostShipmentResponse bought2 = buildBoughtShipment("shp_bulk_002", "USPS", "6.00",
                "https://label.url/002.pdf", "https://track.easypost.com/002");

        given(easyPostApiClient.createShipment(any()))
                .willReturn(created1).willReturn(created2);
        given(easyPostApiClient.buyRate("shp_bulk_001", "r1")).willReturn(bought1);
        given(easyPostApiClient.buyRate("shp_bulk_002", "r2")).willReturn(bought2);
        given(invoiceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<List<OrderInvoicePair>> captor = ArgumentCaptor.forClass(List.class);

        BulkInvoiceResponse response = service.createAndSaveBulkInvoices(
                "seller-001", buildFromAddress(), buildParcel());

        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getFailCount()).isZero();
        verify(easyPostApiClient, times(2)).createShipment(any());
        verify(channelOrderCommandMapper).bulkAssignInvoice(captor.capture());
        List<OrderInvoicePair> pairs = captor.getValue();
        assertThat(pairs).hasSize(2);
        assertThat(pairs.get(0).getOrderId()).isEqualTo("ORD-BULK-001");
        assertThat(pairs.get(0).getInvoiceNo()).isEqualTo("shp_bulk_001");
        assertThat(pairs.get(1).getOrderId()).isEqualTo("ORD-BULK-002");
        assertThat(pairs.get(1).getInvoiceNo()).isEqualTo("shp_bulk_002");
    }

    @Test
    @DisplayName("미발급 주문이 없으면 일괄 송장을 생성했을 때 API를 호출하지 않고 처리 건수를 0으로 반환해야 한다")
    void createAndSaveBulkInvoices_emptyTargets_returnsZero() {
        given(channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-001")).willReturn(List.of());

        BulkInvoiceResponse response = service.createAndSaveBulkInvoices(
                "seller-001", buildFromAddress(), buildParcel());

        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getFailCount()).isZero();
        verify(easyPostApiClient, never()).createShipment(any());
        verify(channelOrderCommandMapper, never()).bulkAssignInvoice(any());
    }

    @Test
    @DisplayName("일부 주문의 EasyPost 발급이 실패하면 일괄 송장을 생성했을 때 성공 건수와 실패 건수를 나누어 반환해야 한다")
    void createAndSaveBulkInvoices_oneFailure_countedAsFail() {
        List<InvoiceTargetDto> targets = List.of(
                buildTarget("ORD-OK-001"), buildTarget("ORD-FAIL-001"));
        given(channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-001")).willReturn(targets);

        EasyPostShipmentResponse created = buildShipmentWithRates("shp_ok_001",
                List.of(buildRate("r1", "USPS", "5.50")));
        EasyPostShipmentResponse bought = buildBoughtShipment("shp_ok_001", "USPS", "5.50",
                "https://label.url/ok.pdf", "https://track.easypost.com/ok");

        given(easyPostApiClient.createShipment(any()))
                .willReturn(created)
                .willThrow(new RuntimeException("EasyPost 연결 오류"));
        given(easyPostApiClient.buyRate("shp_ok_001", "r1")).willReturn(bought);
        given(invoiceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BulkInvoiceResponse response = service.createAndSaveBulkInvoices(
                "seller-001", buildFromAddress(), buildParcel());

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailCount()).isEqualTo(1);
        verify(channelOrderCommandMapper).bulkAssignInvoice(
                List.of(new OrderInvoicePair("ORD-OK-001", "shp_ok_001")));
    }

    @Test
    @DisplayName("미발급 주문 조회 중 예외가 발생하면 일괄 송장을 생성했을 때 호출자에게 예외를 전파해야 한다")
    void createAndSaveBulkInvoices_propagatesMapperException() {
        given(channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-001"))
                .willThrow(new RuntimeException("DB 연결 오류"));

        assertThatThrownBy(() -> service.createAndSaveBulkInvoices(
                "seller-001", buildFromAddress(), buildParcel()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 연결 오류");

        verify(easyPostApiClient, never()).createShipment(any());
    }

    @Test
    @DisplayName("모든 주문의 송장 발급이 실패하면 일괄 송장을 생성했을 때 송장 일괄 반영을 호출하지 않아야 한다")
    void createAndSaveBulkInvoices_skipsBulkAssignWhenAllTargetsFail() {
        List<InvoiceTargetDto> targets = List.of(
                buildTarget("ORD-FAIL-001"), buildTarget("ORD-FAIL-002"));
        given(channelOrderInvoiceMapper.findOrdersWithoutInvoice("seller-001")).willReturn(targets);
        given(easyPostApiClient.createShipment(any()))
                .willThrow(new RuntimeException("EasyPost 연결 오류"));

        BulkInvoiceResponse response = service.createAndSaveBulkInvoices(
                "seller-001", buildFromAddress(), buildParcel());

        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getFailCount()).isEqualTo(2);
        verify(channelOrderCommandMapper, never()).bulkAssignInvoice(any());
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    // bulk 테스트에서 반복되는 주문 타겟 fixture다.
    private InvoiceTargetDto buildTarget(String orderId) {
        InvoiceTargetDto dto = new InvoiceTargetDto();
        dto.setOrderId(orderId);
        dto.setReceiverName("Test Recipient");
        dto.setReceiverPhoneNo("1234567890");
        dto.setShipToAddress1("179 N Harbor Dr");
        dto.setShipToCity("Redondo Beach");
        dto.setShipToState("CA");
        dto.setShipToZipCode("90277");
        return dto;
    }

    // bulk 메서드에 전달하는 발송 주소 fixture다.
    private EasyPostCreateShipmentRequest.AddressBody buildFromAddress() {
        return EasyPostCreateShipmentRequest.AddressBody.builder()
                .name("EasyPost").street1("417 Montgomery St")
                .city("San Francisco").state("CA").zip("94104").country("US")
                .build();
    }

    // bulk 메서드에 전달하는 소포 정보 fixture다.
    private EasyPostCreateShipmentRequest.ParcelBody buildParcel() {
        return EasyPostCreateShipmentRequest.ParcelBody.builder()
                .weight(21.9).length(10.0).width(8.0).height(4.0)
                .build();
    }

    // 서비스가 직렬화해 보내는 최소 shipment 요청 fixture다.
    private EasyPostCreateShipmentRequest buildRequest() {
        return EasyPostCreateShipmentRequest.builder()
                .shipment(EasyPostCreateShipmentRequest.ShipmentBody.builder()
                        .toAddress(EasyPostCreateShipmentRequest.AddressBody.builder()
                                .name("Test Recipient").street1("123 Main St")
                                .city("New York").state("NY").zip("10001").country("US").build())
                        .fromAddress(EasyPostCreateShipmentRequest.AddressBody.builder()
                                .name("Test Sender").street1("417 Montgomery St")
                                .city("San Francisco").state("CA").zip("94104").country("US").build())
                        .parcel(EasyPostCreateShipmentRequest.ParcelBody.builder()
                                .length(20.0).width(10.0).height(5.0).weight(65.9).build())
                        .build())
                .build();
    }

    // createShipment 응답에서 rate 목록만 바꿔가며 재사용하기 위한 헬퍼다.
    private EasyPostShipmentResponse buildShipmentWithRates(String id,
                                                             List<EasyPostShipmentResponse.RateDto> rates) {
        EasyPostShipmentResponse r = new EasyPostShipmentResponse();
        r.setId(id);
        r.setStatus("created");
        r.setRates(rates);
        return r;
    }

    // 가격 비교 테스트에서 반복되는 rate DTO 생성을 줄인다.
    private EasyPostShipmentResponse.RateDto buildRate(String id, String carrier, String rate) {
        EasyPostShipmentResponse.RateDto dto = new EasyPostShipmentResponse.RateDto();
        dto.setId(id);
        dto.setCarrier(carrier);
        dto.setRate(rate);
        return dto;
    }

    // buyRate 이후 필요한 선택 운임, 라벨, 추적 정보가 모두 포함된 응답 fixture다.
    private EasyPostShipmentResponse buildBoughtShipment(String id, String carrier, String rate,
                                                          String labelUrl, String trackingUrl) {
        EasyPostShipmentResponse r = new EasyPostShipmentResponse();
        r.setId(id);

        EasyPostShipmentResponse.RateDto selected = new EasyPostShipmentResponse.RateDto();
        selected.setCarrier(carrier);
        selected.setRate(rate);
        r.setSelectedRate(selected);

        EasyPostShipmentResponse.PostageLabelDto label = new EasyPostShipmentResponse.PostageLabelDto();
        label.setLabelUrl(labelUrl);
        r.setPostageLabel(label);

        EasyPostShipmentResponse.TrackerDto tracker = new EasyPostShipmentResponse.TrackerDto();
        tracker.setPublicUrl(trackingUrl);
        r.setTracker(tracker);

        EasyPostShipmentResponse.AddressDto addr = new EasyPostShipmentResponse.AddressDto();
        addr.setStreet1("123 Main St");
        addr.setCity("New York");
        addr.setState("NY");
        addr.setZip("10001");
        addr.setCountry("US");
        r.setToAddress(addr);

        return r;
    }
}
