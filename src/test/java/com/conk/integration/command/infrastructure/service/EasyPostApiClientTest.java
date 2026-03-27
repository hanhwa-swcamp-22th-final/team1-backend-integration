package com.conk.integration.command.infrastructure.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.infrastructure.config.EasyPostProperties;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("EasyPostApiClient 단위 테스트")
class EasyPostApiClientTest {

    private MockRestServiceServer mockServer;
    private EasyPostApiClient client;
    private EasyPostProperties properties;

    private static final String BASE_URL = "https://api.easypost.com";
    private static final String TEST_API_KEY = "EZTK_test_key";

    @BeforeEach
    void setUp() {
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
    @DisplayName("[GREEN] createShipment - 정상 응답 시 shipment id와 rates 반환")
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
    @DisplayName("[GREEN] createShipment - Basic Auth 헤더 포함 확인")
    void createShipment_includesBasicAuthHeader() {
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
    @DisplayName("[GREEN] createShipment - rates가 빈 배열인 경우")
    void createShipment_returnsEmptyRates_whenNoRatesAvailable() {
        String json = "{\"id\":\"shp_empty\",\"status\":\"created\",\"rates\":[]}";
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        EasyPostShipmentResponse response = client.createShipment(buildRequest());

        assertThat(response.getRates()).isEmpty();
    }

    @Test
    @DisplayName("[예외] createShipment - 401 Unauthorized → HttpClientErrorException")
    void createShipment_throws_whenUnauthorized() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.createShipment(buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("[예외] createShipment - 422 Unprocessable Entity → HttpClientErrorException")
    void createShipment_throws_whenUnprocessableEntity() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> client.createShipment(buildRequest()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("[예외] createShipment - 500 서버 오류 → HttpServerErrorException")
    void createShipment_throws_whenServerError() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.createShipment(buildRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // ─────────────────────────────────────────────────────────
    // buyRate
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("[GREEN] buyRate - 정상 응답 시 label_url, tracking 반환")
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
    @DisplayName("[GREEN] buyRate - shipmentId가 URL에 올바르게 포함")
    void buyRate_usesCorrectUrlWithShipmentId() {
        String shipmentId = "shp_xyz_999";
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments/" + shipmentId + "/buy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"" + shipmentId + "\"}", MediaType.APPLICATION_JSON));

        client.buyRate(shipmentId, "rate_abc");
        mockServer.verify();
    }

    @Test
    @DisplayName("[예외] buyRate - 500 서버 오류 → HttpServerErrorException")
    void buyRate_throws_whenServerError() {
        mockServer.expect(requestTo(BASE_URL + "/v2/shipments/shp_001/buy"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.buyRate("shp_001", "rate_001"))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // ─────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────

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
