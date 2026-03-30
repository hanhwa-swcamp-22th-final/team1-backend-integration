package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.domain.aggregate.CarrierType;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.service.EasyPostApiClient;
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
import static org.mockito.Mockito.*;

// EasyPost 송장 저장 서비스의 정상 흐름과 예외 전파를 Mockito로 검증한다.
@ExtendWith(MockitoExtension.class)
@DisplayName("EasyPostInvoiceSaveService 단위 테스트")
class EasyPostInvoiceSaveServiceTest {

    @Mock private EasyPostApiClient easyPostApiClient;
    @Mock private EasypostShipmentInvoiceRepository invoiceRepository;

    @InjectMocks
    private EasyPostInvoiceSaveService service;

    // ─────────────────────────────────────────────────────────
    // Happy Path
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] 정상 플로우 - createShipment → buyRate → DB 저장")
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
    @DisplayName("[GREEN] 최저가 rate가 올바르게 선택됨")
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
    @DisplayName("[GREEN] FedEx carrier는 FEDEX enum으로 매핑")
    void resolveCarrierType_fedex() {
        assertThat(service.resolveCarrierType("FedEx")).isEqualTo(CarrierType.FEDEX);
        assertThat(service.resolveCarrierType("FEDEX")).isEqualTo(CarrierType.FEDEX);
    }

    @Test
    @DisplayName("[GREEN] UPS carrier는 UPS enum으로 매핑")
    void resolveCarrierType_ups() {
        assertThat(service.resolveCarrierType("UPS")).isEqualTo(CarrierType.UPS);
    }

    @Test
    @DisplayName("[GREEN] 알 수 없는 carrier는 USPS enum으로 매핑")
    void resolveCarrierType_unknown() {
        assertThat(service.resolveCarrierType("DHL")).isEqualTo(CarrierType.USPS);
        assertThat(service.resolveCarrierType(null)).isEqualTo(CarrierType.USPS);
    }

    @Test
    @DisplayName("[GREEN] 필드 매핑 - invoiceNo, carrierType, freightChargeAmt, labelUrl 검증")
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
    @DisplayName("[예외] rates가 null이면 IllegalStateException")
    void selectCheapestRate_throwsWhenNull() {
        assertThatThrownBy(() -> service.selectCheapestRate(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No rates available");
    }

    @Test
    @DisplayName("[예외] rates가 빈 리스트이면 IllegalStateException")
    void selectCheapestRate_throwsWhenEmpty() {
        assertThatThrownBy(() -> service.selectCheapestRate(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No rates available");
    }

    // ─────────────────────────────────────────────────────────
    // 예외 전파
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[예외] createShipment 401 → 예외 전파, save 미호출")
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
    @DisplayName("[예외] buyRate 500 → 예외 전파, save 미호출")
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
    @DisplayName("[예외] createShipment 응답에 rates가 없으면 IllegalStateException")
    void createAndSaveInvoice_throwsWhenNoRates() {
        // 구매 가능한 rate가 없으면 저장 전에 즉시 실패해야 한다.
        EasyPostShipmentResponse created = buildShipmentWithRates("shp_001", List.of());
        given(easyPostApiClient.createShipment(any())).willReturn(created);

        assertThatThrownBy(() -> service.createAndSaveInvoice(buildRequest()))
                .isInstanceOf(IllegalStateException.class);
        verify(invoiceRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

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
