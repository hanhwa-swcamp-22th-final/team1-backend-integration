package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.infrastructure.config.EasyPostProperties;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// EasyPostApiClient가 URL, 인증 헤더, 응답 파싱을 올바르게 수행하는지 검증한다.
@DisplayName("EasyPostApiClient 테스트")
class EasyPostApiClientTest {

    private MockRestServiceServer mockServer;
    private EasyPostApiClient client;
    private EasyPostProperties properties;

    private static final String BASE_URL = "https://api.easypost.com";
    private static final String TEST_API_KEY = "EZTK_test_key";

    @BeforeEach
    void setUp() {
        // 실제 HTTP 대신 MockRestServiceServer로 요청/응답 경계를 고정한다.
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        properties = new EasyPostProperties();
        properties.setApiKey(TEST_API_KEY);
        properties.setBaseUrl(BASE_URL);

        client = new EasyPostApiClient(restTemplate, properties, new ObjectMapper());
    }

    // ─────────────────────────────────────────────────────────
    // createShipment
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 응답이 주어지면 createShipment를 호출했을 때 shipment id와 rates를 반환해야 한다")
    void createShipment_returnsShipmentWithRates_whenSuccessful() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(shipmentResponseJson("shp_test_001"), MediaType.APPLICATION_JSON));

        EasyPostCreateShipmentRequest req = buildRequest();
        EasyPostShipmentResponse response = client.createShipment(req);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("shp_test_001");
        assertThat(response.getRates()).hasSize(2);
        mockServer.verify();
    }

    @Test
    @DisplayName("요청 정보를 직렬화할 수 있으면 createShipment를 호출했을 때 Basic Auth 헤더를 포함해야 한다")
    void createShipment_includesBasicAuthHeader() {
        // EasyPost는 API key를 Basic Auth로 요구하므로 헤더 구성이 핵심이다.
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                (TEST_API_KEY + ":").getBytes(StandardCharsets.UTF_8));

        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", expectedAuth))
                .andRespond(withSuccess(shipmentResponseJson("shp_test_002"), MediaType.APPLICATION_JSON));

        client.createShipment(buildRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("운임 정보가 빈 배열인 응답이 주어지면 createShipment를 호출했을 때 빈 rates를 반환해야 한다")
    void createShipment_returnsEmptyRates_whenNoRatesAvailable() {
        String json = "{\"id\":\"shp_empty\",\"status\":\"created\",\"rates\":[]}";
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        EasyPostShipmentResponse response = client.createShipment(buildRequest());

        assertThat(response.getRates()).isEmpty();
    }

    @Test
    @DisplayName("401 응답이 주어지면 createShipment를 호출했을 때 HttpClientErrorException을 전파해야 한다")
    void createShipment_throws_whenUnauthorized() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.createShipment(buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("422 응답이 주어지면 createShipment를 호출했을 때 HttpClientErrorException을 전파해야 한다")
    void createShipment_throws_whenUnprocessableEntity() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> client.createShipment(buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("500 응답이 주어지면 createShipment를 호출했을 때 HttpServerErrorException을 전파해야 한다")
    void createShipment_throws_whenServerError() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.createShipment(buildRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("shipment body가 null이면 createShipment를 호출했을 때 BusinessException(INT-002)를 발생시켜야 한다")
    void createShipment_throwsWhenShipmentBodyIsNull() {
        EasyPostCreateShipmentRequest request = EasyPostCreateShipmentRequest.builder().build();

        assertThatThrownBy(() -> client.createShipment(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SHIPMENT_BODY_REQUIRED);
    }

    @Test
    @DisplayName("응답 body가 null이면 createShipment를 호출했을 때 BusinessException(INT-303)을 발생시켜야 한다")
    void createShipment_throwsWhenResponseBodyIsNull() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EasyPostApiClient localClient = new EasyPostApiClient(restTemplate, properties, new ObjectMapper());

        given(restTemplate.exchange(
                org.mockito.ArgumentMatchers.eq(properties.getShipmentsUrl()),
                org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
                org.mockito.ArgumentMatchers.<org.springframework.http.HttpEntity<String>>any(),
                org.mockito.ArgumentMatchers.eq(EasyPostShipmentResponse.class)
        )).willReturn(org.springframework.http.ResponseEntity.<EasyPostShipmentResponse>ok(null));

        assertThatThrownBy(() -> localClient.createShipment(buildRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EASYPOST_EMPTY_RESPONSE);
    }

    @Test
    @DisplayName("JSON 직렬화에 실패하면 createShipment를 호출했을 때 BusinessException(INT-304)을 발생시켜야 한다")
    void createShipment_throwsSerializationFailedWhenJsonWriteFails() {
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("serialization failed") { };
            }
        };
        EasyPostApiClient localClient = new EasyPostApiClient(new RestTemplate(), properties, failingMapper);

        assertThatThrownBy(() -> localClient.createShipment(buildRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EASYPOST_SERIALIZATION_FAILED);
    }

    // ─────────────────────────────────────────────────────────
    // buyRate
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 응답이 주어지면 buyRate를 호출했을 때 labelUrl과 tracking 정보를 반환해야 한다")
    void buyRate_returnsShipmentWithLabelAndTracking_whenSuccessful() {
        String shipmentId = "shp_test_001";
        String rateId = "rate_001";
        String json = "{"
                + "\"id\":\"" + shipmentId + "\","
                + "\"selected_rate\":{\"id\":\"" + rateId + "\",\"carrier\":\"USPS\",\"rate\":\"6.40\"},"
                + "\"postage_label\":{\"label_url\":\"https://easypost.com/label.pdf\"},"
                + "\"tracker\":{\"id\":\"trk_001\",\"public_url\":\"https://track.easypost.com/abc\"}"
                + "}";

        mockServer.expect(requestTo(BASE_URL + "/v2/shipments/" + shipmentId + "/buy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        EasyPostShipmentResponse response = client.buyRate(shipmentId, rateId);

        assertThat(response.getPostageLabel().getLabelUrl()).isEqualTo("https://easypost.com/label.pdf");
        assertThat(response.getTracker().getPublicUrl()).isEqualTo("https://track.easypost.com/abc");
        assertThat(response.getSelectedRate().getRate()).isEqualTo("6.40");
        mockServer.verify();
    }

    @Test
    @DisplayName("shipmentId가 주어지면 buyRate를 호출했을 때 요청 URL에 shipmentId를 포함해야 한다")
    void buyRate_usesCorrectUrlWithShipmentId() {
        // buyRate는 shipmentId가 경로에 직접 들어가므로 URL 조합을 확인한다.
        String shipmentId = "shp_xyz_999";
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments/" + shipmentId + "/buy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"" + shipmentId + "\"}", MediaType.APPLICATION_JSON));

        client.buyRate(shipmentId, "rate_abc");
        mockServer.verify();
    }

    @Test
    @DisplayName("500 응답이 주어지면 buyRate를 호출했을 때 HttpServerErrorException을 전파해야 한다")
    void buyRate_throws_whenServerError() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments/shp_001/buy"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.buyRate("shp_001", "rate_001"))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("응답 body가 null이면 buyRate를 호출했을 때 BusinessException(INT-303)을 발생시켜야 한다")
    void buyRate_throwsWhenResponseBodyIsNull() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EasyPostApiClient localClient = new EasyPostApiClient(restTemplate, properties, new ObjectMapper());

        given(restTemplate.exchange(
                org.mockito.ArgumentMatchers.eq(properties.getBuyRateUrl("shp_001")),
                org.mockito.ArgumentMatchers.eq(HttpMethod.POST),
                org.mockito.ArgumentMatchers.<org.springframework.http.HttpEntity<String>>any(),
                org.mockito.ArgumentMatchers.eq(EasyPostShipmentResponse.class)
        )).willReturn(org.springframework.http.ResponseEntity.<EasyPostShipmentResponse>ok(null));

        assertThatThrownBy(() -> localClient.buyRate("shp_001", "rate_001"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EASYPOST_EMPTY_RESPONSE);
    }

    @Test
    @DisplayName("JSON 직렬화에 실패하면 buyRate를 호출했을 때 BusinessException(INT-304)을 발생시켜야 한다")
    void buyRate_throwsSerializationFailedWhenJsonWriteFails() {
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("serialization failed") { };
            }
        };
        EasyPostApiClient localClient = new EasyPostApiClient(new RestTemplate(), properties, failingMapper);

        assertThatThrownBy(() -> localClient.buyRate("shp_001", "rate_001"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EASYPOST_SERIALIZATION_FAILED);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

    // 클라이언트가 직렬화할 최소 shipment 요청 fixture다.
    private EasyPostCreateShipmentRequest buildRequest() {
        return EasyPostCreateShipmentRequest.builder()
                .shipment(EasyPostCreateShipmentRequest.ShipmentBody.builder()
                        .toAddress(EasyPostCreateShipmentRequest.AddressBody.builder()
                                .name("John Doe").street1("417 Montgomery St")
                                .city("San Francisco").state("CA").zip("94104").country("US")
                                .build())
                        .fromAddress(EasyPostCreateShipmentRequest.AddressBody.builder()
                                .name("EasyPost").street1("417 Montgomery St")
                                .city("San Francisco").state("CA").zip("94104").country("US")
                                .build())
                        .parcel(EasyPostCreateShipmentRequest.ParcelBody.builder()
                                .length(20.2).width(10.9).height(5.0).weight(65.9)
                                .build())
                        .build())
                .build();
    }

    // createShipment 응답 파싱 테스트에 재사용하는 JSON fixture다.
    private String shipmentResponseJson(String id) {
        return "{"
                + "\"id\":\"" + id + "\","
                + "\"status\":\"created\","
                + "\"rates\":["
                + "  {\"id\":\"rate_001\",\"carrier\":\"USPS\",\"service\":\"Priority\",\"rate\":\"6.40\",\"currency\":\"USD\"},"
                + "  {\"id\":\"rate_002\",\"carrier\":\"UPS\",\"service\":\"Ground\",\"rate\":\"10.50\",\"currency\":\"USD\"}"
                + "]"
                + "}";
    }
}
