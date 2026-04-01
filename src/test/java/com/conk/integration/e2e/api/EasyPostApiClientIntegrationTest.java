package com.conk.integration.e2e.api;
import com.conk.integration.command.infrastructure.service.EasyPostApiClient;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.BulkInvoiceResponse;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.application.service.EasyPostInvoiceSaveService;
import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.domain.repository.ChannelOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EasyPost 샌드박스 통합 테스트
 * 실행: ./gradlew sandboxTest
 * 전제: application-dev.yml 에 easypost.api-key (EZTK...) 입력 필요
 * 실제 결제 없음 — test mode API key 사용
 */
@Tag("sandbox")
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("EasyPost 샌드박스 통합 테스트")
class EasyPostApiClientIntegrationTest {

    @Autowired
    private EasyPostApiClient easyPostApiClient;

    @Autowired
    private EasyPostInvoiceSaveService easyPostInvoiceSaveService;

    @Autowired
    private ChannelOrderRepository channelOrderRepository;

    // EasyPost 공식 샌드박스 주소
    private static final EasyPostCreateShipmentRequest.AddressBody TO_ADDRESS =
            EasyPostCreateShipmentRequest.AddressBody.builder()
                    .name("Dr. Steve Brule")
                    .street1("179 N Harbor Dr")
                    .city("Redondo Beach")
                    .state("CA")
                    .zip("90277")
                    .country("US")
                    .phone("4155559999")
                    .email("test@example.com")
                    .build();

    private static final EasyPostCreateShipmentRequest.AddressBody FROM_ADDRESS =
            EasyPostCreateShipmentRequest.AddressBody.builder()
                    .name("EasyPost")
                    .street1("417 Montgomery St")
                    .city("San Francisco")
                    .state("CA")
                    .zip("94104")
                    .country("US")
                    .phone("4153334445")
                    .email("support@easypost.com")
                    .build();

    private static final EasyPostCreateShipmentRequest.ParcelBody PARCEL =
            EasyPostCreateShipmentRequest.ParcelBody.builder()
                    .length(20.2)
                    .width(10.9)
                    .height(5.0)
                    .weight(65.9)
                    .build();

    // 실제 샌드박스 shipment 생성 후 rate 목록이 내려오는지만 우선 검증한다.
    @Test
    @DisplayName("[샌드박스] createShipment - 실제 API 호출 후 rates 반환 확인")
    void createShipment_returnsRates_fromSandbox() {
        EasyPostCreateShipmentRequest request = EasyPostCreateShipmentRequest.builder()
                .shipment(EasyPostCreateShipmentRequest.ShipmentBody.builder()
                        .toAddress(TO_ADDRESS)
                        .fromAddress(FROM_ADDRESS)
                        .parcel(PARCEL)
                        .build())
                .build();

        EasyPostShipmentResponse response = easyPostApiClient.createShipment(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotBlank();
        assertThat(response.getRates()).isNotEmpty();

        System.out.println("[EasyPost Sandbox] Shipment ID: " + response.getId());
        response.getRates().forEach(r ->
                System.out.printf("  - Carrier: %-8s Service: %-20s Rate: $%s%n",
                        r.getCarrier(), r.getService(), r.getRate()));
    }

    // 생성-구매-저장 전체 흐름을 샌드박스 환경에서 한 번에 검증한다.
    @Test
    @DisplayName("[샌드박스] createAndSaveInvoice - 전체 플로우 (createShipment → buyRate → DB 저장)")
    void createAndSaveInvoice_fullFlow_fromSandbox() {
        EasyPostCreateShipmentRequest request = EasyPostCreateShipmentRequest.builder()
                .shipment(EasyPostCreateShipmentRequest.ShipmentBody.builder()
                        .toAddress(TO_ADDRESS)
                        .fromAddress(FROM_ADDRESS)
                        .parcel(PARCEL)
                        .build())
                .build();

        EasypostShipmentInvoice invoice = easyPostInvoiceSaveService.createAndSaveInvoice(request);

        assertThat(invoice).isNotNull();
        assertThat(invoice.getInvoiceNo()).isNotBlank();
        assertThat(invoice.getCarrierType()).isNotNull();
        assertThat(invoice.getFreightChargeAmt()).isGreaterThan(0);
        assertThat(invoice.getLabelFileUrl()).isNotBlank();

        System.out.println("[EasyPost Sandbox] Invoice saved:");
        System.out.println("  InvoiceNo    : " + invoice.getInvoiceNo());
        System.out.println("  CarrierType  : " + invoice.getCarrierType());
        System.out.println("  FreightCharge: $" + (invoice.getFreightChargeAmt() / 100.0));
        System.out.println("  LabelUrl     : " + invoice.getLabelFileUrl());
        System.out.println("  TrackingUrl  : " + invoice.getTrackingUrl());
    }

    // 여러 주문에 대해 일괄 송장 발급이 정상 동작하는지 샌드박스에서 확인한다.
    @Test
    @Transactional
    @DisplayName("[샌드박스] createAndSaveBulkInvoices - 주문 2건 일괄 발급 후 invoiceNo 반영 확인")
    void createAndSaveBulkInvoices_bulkFlow_fromSandbox() {
        // given — invoiceNo 없는 테스트 주문 2건을 DB에 저장
        channelOrderRepository.saveAndFlush(ChannelOrder.builder()
                .orderId("SANDBOX-BULK-001")
                .channelOrderNo("#SANDBOX-001")
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId("seller-sandbox-bulk")
                .receiverName("Dr. Steve Brule")
                .receiverPhoneNo("4155559999")
                .shipToAddress1("179 N Harbor Dr")
                .shipToCity("Redondo Beach")
                .shipToState("CA")
                .shipToZipCode("90277")
                .build());
        channelOrderRepository.saveAndFlush(ChannelOrder.builder()
                .orderId("SANDBOX-BULK-002")
                .channelOrderNo("#SANDBOX-002")
                .orderChannel(OrderChannel.SHOPIFY)
                .sellerId("seller-sandbox-bulk")
                .receiverName("EasyPost Support")
                .receiverPhoneNo("4153334445")
                .shipToAddress1("417 Montgomery St")
                .shipToCity("San Francisco")
                .shipToState("CA")
                .shipToZipCode("94104")
                .build());

        EasyPostCreateShipmentRequest.AddressBody fromAddress =
                EasyPostCreateShipmentRequest.AddressBody.builder()
                        .name("EasyPost")
                        .street1("417 Montgomery St")
                        .city("San Francisco")
                        .state("CA")
                        .zip("94104")
                        .country("US")
                        .phone("4153334445")
                        .email("support@easypost.com")
                        .build();

        EasyPostCreateShipmentRequest.ParcelBody parcel =
                EasyPostCreateShipmentRequest.ParcelBody.builder()
                        .length(20.2).width(10.9).height(5.0).weight(65.9)
                        .build();

        // when
        BulkInvoiceResponse response = easyPostInvoiceSaveService.createAndSaveBulkInvoices(
                "seller-sandbox-bulk", fromAddress, parcel);

        // then
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getFailCount()).isZero();

        // DB에 invoiceNo가 반영되었는지 확인
        assertThat(channelOrderRepository.findById("SANDBOX-BULK-001").orElseThrow().getInvoiceNo())
                .isNotBlank();
        assertThat(channelOrderRepository.findById("SANDBOX-BULK-002").orElseThrow().getInvoiceNo())
                .isNotBlank();

        System.out.println("[EasyPost Sandbox] Bulk invoices issued:");
        System.out.println("  SuccessCount : " + response.getSuccessCount());
        System.out.println("  FailCount    : " + response.getFailCount());
        System.out.println("  InvoiceNo(1) : " + channelOrderRepository.findById("SANDBOX-BULK-001").orElseThrow().getInvoiceNo());
        System.out.println("  InvoiceNo(2) : " + channelOrderRepository.findById("SANDBOX-BULK-002").orElseThrow().getInvoiceNo());
    }
}
