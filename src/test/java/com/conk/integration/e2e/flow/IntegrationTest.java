package com.conk.integration.e2e.flow;

import com.conk.integration.command.application.dto.response.ChannelOrderSyncResponse;
import com.conk.integration.command.domain.aggregate.*;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.*;
import com.conk.integration.query.mapper.ChannelApiMapper;
import com.conk.integration.command.application.service.ChannelFulfillmentDispatchService;
import com.conk.integration.command.application.service.shopify.ShopifyOrderSyncService;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyOrderClient;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyFulfillmentApiClient;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyOrderResponse;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.query.service.ChannelApiQueryService;
import com.conk.integration.command.infrastructure.service.shopify.ShopifyPingClient;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * [5단계] 통합 테스트 — @SpringBootTest
 *
 * - @SpringBootTest : 전체 ApplicationContext를 로드합니다 (Controller → Service → DB).
 * - @AutoConfigureMockMvc : 실제 서블릿 컨테이너 없이 MockMvc로 HTTP 테스트가 가능합니다.
 * - @ActiveProfiles("test") : H2 인메모리 DB를 사용하는 test 프로파일을 적용합니다.
 * - @Transactional : 각 테스트 후 DB를 자동으로 롤백합니다.
 *
 * ⚠ 외부 API(Shopify, EasyPost)는 실제 호출 없이 @MockitoBean으로만 대체합니다.
 *   나머지 Controller → Service → DB 전체 흐름은 실제 빈을 사용합니다.
 *
 * ✅ 검증 대상:
 *   - ShopifyOrderSyncService.syncOrders() : API 응답 → DB 저장 전체 흐름
 *   - ChannelFulfillmentDispatchService.fulfill() : DB 조회 → 채널 선택 → Shopify API 호출 전체 흐름
 *   - GET /integrations/seller/orders : Controller → Service → DB → HTTP 응답 전체 흐름
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("전체 시스템 통합 테스트")
class IntegrationTest {

    /* ---------- MockMvc (HTTP 계층 테스트) ---------- */
    @Autowired
    private MockMvc mockMvc;

    /* ---------- 외부 API — MockitoBean으로만 격리 ---------- */
    @MockitoBean
    private ShopifyOrderClient shopifyOrderClient;

    @MockitoBean
    private ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;

    @MockitoBean
    private ChannelApiQueryService channelApiQueryService;

    @MockitoBean
    private ShopifyPingClient shopifyPingClient;

    /* ---------- 실제 Bean ---------- */
    @Autowired
    private ShopifyOrderSyncService shopifyOrderSyncService;

    @Autowired
    private ChannelFulfillmentDispatchService fulfillmentDispatchService;

    @Autowired
    private ChannelOrderRepository channelOrderRepository;

    @Autowired
    private EasypostShipmentInvoiceRepository invoiceRepository;

    @Autowired
    private ChannelApiRepository channelApiRepository;

    @Autowired
    private ChannelApiMapper channelApiMapper;

    @BeforeEach
    void setUpChannelSupport() {
        given(shopifyPingClient.supports("SHOPIFY")).willReturn(true);
    }

    /* ===================================================================
     * 1) ShopifyOrderSyncService — 전체 흐름 (API → DB 저장)
     * =================================================================== */

    @Nested
    @DisplayName("Shopify 주문 동기화 통합 테스트")
    class SyncOrdersIntegrationTests {

        // 외부 주문 응답이 실제 DB 저장으로 이어지는지 본다.
        @Test
        @DisplayName("Shopify 주문 2건이 주어지면 주문 동기화를 수행했을 때 DB에 2건을 저장해야 한다")
        void syncOrders_persistsTwoOrders() {
            // given
            ShopifyOrderResponse.OrderNode dto1 = buildShopifyOrderNode(9001L, "#9001", "Alice", "100 Main St");
            ShopifyOrderResponse.OrderNode dto2 = buildShopifyOrderNode(9002L, "#9002", "Bob",   "200 Oak Ave");

            given(channelApiQueryService.findChannelCredential("seller-integration-A", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(dto1, dto2));

            // when
            shopifyOrderSyncService.syncOrders("seller-integration-A");

            // then — DB에서 직접 조회하여 2건 확인
            List<ChannelOrder> saved = channelOrderRepository.findBySellerId("seller-integration-A");
            assertThat(saved).hasSize(2);
            assertThat(saved).extracting(ChannelOrder::getChannelOrderNo)
                    .containsExactlyInAnyOrder("#9001", "#9002");
        }

        @Test
        @DisplayName("이미 저장된 주문이 다시 주어지면 주문 동기화를 수행했을 때 중복 저장하지 않아야 한다")
        void syncOrders_idempotent_doesNotDuplicateExistingOrder() {
            // 같은 주문이 다시 들어와도 DB 건수는 유지되어야 한다.
            // given — 주문 9003은 이미 DB에 존재
            channelOrderRepository.save(ChannelOrder.builder()
                    .orderId("9003").channelOrderNo("#9003")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-integration-B").build());

            // Shopify API는 동일한 주문을 다시 반환
            ShopifyOrderResponse.OrderNode existingNode = buildShopifyOrderNode(9003L, "#9003", "Charlie", "300 Pine Rd");
            given(channelApiQueryService.findChannelCredential("seller-integration-B", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(existingNode));

            // when
            shopifyOrderSyncService.syncOrders("seller-integration-B");

            // then — 여전히 1건만 존재 (중복 저장 없음)
            List<ChannelOrder> result = channelOrderRepository.findBySellerId("seller-integration-B");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Shopify 주문이 비어 있으면 주문 동기화를 수행했을 때 DB에 저장하지 않아야 한다")
        void syncOrders_emptyResponse_savesNothing() {
            // given
            given(channelApiQueryService.findChannelCredential("seller-integration-C", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of());

            // when
            shopifyOrderSyncService.syncOrders("seller-integration-C");

            // then
            List<ChannelOrder> result = channelOrderRepository.findBySellerId("seller-integration-C");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("lineItems가 포함된 주문이 주어지면 주문 동기화를 수행했을 때 주문 아이템도 함께 저장해야 한다")
        void syncOrders_persistsChannelOrderItems_whenLineItemsPresent() {
            // given
            ShopifyOrderResponse.OrderNode node = buildShopifyOrderNode(9010L, "#9010", "Diana", "400 Elm St");
            node.setLineItems(buildLineItemConnection("SKU-A", "Widget A", 2, null));

            given(channelApiQueryService.findChannelCredential("seller-integration-D", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));

            // when
            shopifyOrderSyncService.syncOrders("seller-integration-D");

            // then — DB에서 주문을 다시 조회해 items 확인
            channelOrderRepository.flush();
            ChannelOrder saved = channelOrderRepository.findById("9010").orElseThrow();
            assertThat(saved.getItems()).hasSize(1);
            assertThat(saved.getItems().get(0).getId().getSkuId()).isEqualTo("SKU-A");
            assertThat(saved.getItems().get(0).getProductNameSnapshot()).isEqualTo("Widget A");
            assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("기존 주문과 신규 주문이 함께 주어지면 주문 동기화를 수행했을 때 savedCount와 skippedCount를 정확히 반환해야 한다")
        void syncOrders_returnsCorrectSavedAndSkippedCount() {
            // given — 9020은 이미 DB에 존재, 9021은 신규
            channelOrderRepository.save(ChannelOrder.builder()
                    .orderId("9020").channelOrderNo("#9020")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-integration-E").build());

            ShopifyOrderResponse.OrderNode existing = buildShopifyOrderNode(9020L, "#9020", "Eve", "500 Pine St");
            ShopifyOrderResponse.OrderNode newOne   = buildShopifyOrderNode(9021L, "#9021", "Frank", "600 Oak Ave");

            given(channelApiQueryService.findChannelCredential("seller-integration-E", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(existing, newOne));

            // when
            ChannelOrderSyncResponse result = shopifyOrderSyncService.syncOrders("seller-integration-E");

            // then
            assertThat(result.getSavedCount()).isEqualTo(1);
            assertThat(result.getSkippedCount()).isEqualTo(1);
            assertThat(result.getOrders()).hasSize(1);
            assertThat(result.getOrders().get(0).getOrderId()).isEqualTo("9021");
        }
    }

    /* ===================================================================
     * 2) ChannelFulfillmentDispatchService — 전체 흐름 (DB 조회 → Shopify API 호출)
     * =================================================================== */

    @Nested
    @DisplayName("fulfillment 전송 통합 테스트")
    class FulfillmentIntegrationTests {

        // DB에 필요한 데이터가 있으면 외부 fulfillment API까지 이어지는지 본다.
        @Test
        @DisplayName("주문과 송장 정보가 DB에 저장되어 있으면 fulfillment를 수행했을 때 Shopify fulfillment API를 호출해야 한다")
        void fulfill_callsShopifyApiWhenDataExists() {
            // given — Invoice와 ChannelOrder를 DB에 미리 저장
            invoiceRepository.save(EasypostShipmentInvoice.builder()
                    .invoiceNo("INV-INTEGRATION-001")
                    .carrierType(CarrierType.UPS)
                    .freightChargeAmt(1500)
                    .build());

            channelOrderRepository.save(ChannelOrder.builder()
                    .orderId("ORDER-INTG-001")
                    .channelOrderNo("#INTG-001")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-X")
                    .invoiceNo("INV-INTEGRATION-001")
                    .build());

            given(channelApiQueryService.findChannelCredential("seller-X", "SHOPIFY")).willReturn(buildCredential());

            // when
            fulfillmentDispatchService.fulfill("ORDER-INTG-001");

            // then — Shopify fulfillment API가 실제로 호출되었는지 검증
            then(shopifyFulfillmentApiClient).should(times(1))
                    .createFulfillment(anyString(), anyString(), eq("#INTG-001"), any());
        }

        @Test
        @DisplayName("DB에 주문이 없으면 fulfillment를 수행했을 때 BusinessException(INT-101)을 발생시켜야 한다")
        void fulfill_throwsWhenOrderNotInDb() {
            assertThatThrownBy(() -> fulfillmentDispatchService.fulfill("ORDER-NOT-EXIST"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("송장 번호가 없는 주문이 주어지면 fulfillment를 수행했을 때 BusinessException(INT-202)를 발생시켜야 한다")
        void fulfill_throwsWhenNoInvoiceInOrder() {
            // given — invoiceNo가 없는 주문
            channelOrderRepository.save(ChannelOrder.builder()
                    .orderId("ORDER-NO-INV")
                    .channelOrderNo("#NO-INV")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-Y")
                    .invoiceNo(null)
                    .build());

            // when & then
            assertThatThrownBy(() -> fulfillmentDispatchService.fulfill("ORDER-NO-INV"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("송장이 발급되지 않은 주문");
        }
    }

    /* ===================================================================
     * 3) HTTP → Controller → Service → DB 전체 흐름
     * =================================================================== */

    @Nested
    @DisplayName("주문 조회 HTTP 통합 테스트")
    class OrderQueryHttpIntegrationTests {

        // MyBatis 조회 결과가 HTTP 응답 JSON까지 전달되는 전체 경로를 확인한다.
        @Test
        @DisplayName("DB에 주문이 저장되어 있으면 주문 조회를 요청했을 때 HTTP 200 응답과 주문 데이터를 반환해야 한다")
        void getOrders_e2e_returnsHttpOkWithData() throws Exception {
            // given — DB에 직접 저장 (JPA 영속성 컨텍스트를 DB와 동기화하여 MyBatis에서 읽을 수 있게 flush 처리)
            channelOrderRepository.saveAndFlush(ChannelOrder.builder()
                    .orderId("E2E-ORD-001")
                    .channelOrderNo("#E2E-001")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-E2E")
                    .receiverName("E2E 수신자")
                    .orderedAt(LocalDateTime.of(2024, 6, 1, 10, 0))
                    .build());

            // when & then — HTTP 요청을 보내고 응답 전체를 검증
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-E2E"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(20))
                    .andExpect(jsonPath("$.data.items[0].id").value("E2E-ORD-001"))
                    .andExpect(jsonPath("$.data.items[0].channel").value("SHOPIFY"))
                    .andExpect(jsonPath("$.data.items[0].recipient").value("E2E 수신자"))
                    .andExpect(jsonPath("$.data.items[0].orderedAt").value("2024-06-01 10:00"))
                    .andExpect(jsonPath("$.data.items[0].status").value("NEW"));
        }

        @Test
        @DisplayName("0 페이지 요청이 주어지면 주문 조회를 요청했을 때 HTTP 200 응답과 첫 페이지 데이터를 반환해야 한다")
        void getOrders_e2e_zeroBasedPage_returnsHttpOk() throws Exception {
            // given
            channelOrderRepository.saveAndFlush(ChannelOrder.builder()
                    .orderId("E2E-ORD-PAGE-001")
                    .channelOrderNo("#E2E-PAGE-001")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-E2E-page")
                    .receiverName("페이지 수신자")
                    .orderedAt(LocalDateTime.of(2024, 6, 3, 10, 0))
                    .build());

            // when & then
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-E2E-page")
                            .param("page", "0")
                            .param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(100))
                    .andExpect(jsonPath("$.data.items[0].id").value("E2E-ORD-PAGE-001"));
        }

        @Test
        @DisplayName("DB에 주문이 저장되어 있지 않으면 주문 조회를 요청했을 때 HTTP 200 응답과 빈 배열을 반환해야 한다")
        void getOrders_e2e_returnsEmptyWhenNoData() throws Exception {
            // when & then — 데이터 없는 sellerId로 조회
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-EMPTY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items").isEmpty())
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(20));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 주문 조회를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void getOrders_e2e_missingHeader_returns400() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("송장 번호가 있는 주문이 저장되어 있으면 주문 조회를 요청했을 때 PROCESSING 상태를 반환해야 한다")
        void getOrders_e2e_processingStatusWhenInvoiceExists() throws Exception {
            // given — DB에 직접 저장하고 flush 처리
            channelOrderRepository.saveAndFlush(ChannelOrder.builder()
                    .orderId("E2E-ORD-002")
                    .channelOrderNo("#E2E-002")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-E2E-2")
                    .receiverName("처리중 수신자")
                    .invoiceNo("INV-E2E-001") // invoiceNo 있음 → PROCESSING
                    .orderedAt(LocalDateTime.of(2024, 6, 2, 12, 0))
                    .build());

            // when & then
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-E2E-2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].status").value("PROCESSING"))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(20));
        }
    }

    /* ===================================================================
     * 4) POST /integrations/seller/orders/sync — HTTP 전체 흐름
     * =================================================================== */

    @Nested
    @DisplayName("주문 동기화 HTTP 통합 테스트")
    class SyncOrdersHttpIntegrationTests {

        // HTTP 요청 → Controller → DispatchService → SyncService → DB 저장 전체 흐름을 확인한다.
        @Test
        @DisplayName("유효한 주문 동기화 요청이 주어지면 주문 동기화를 요청했을 때 HTTP 200 응답과 처리 건수를 반환해야 한다")
        void syncOrders_e2e_returnsHttpOkWithCounts() throws Exception {
            // given
            ShopifyOrderResponse.OrderNode node = buildShopifyOrderNode(8001L, "#8001", "Alice", "100 Main St");
            given(channelApiQueryService.findChannelCredential("seller-http-A", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));

            // when & then
            mockMvc.perform(post("/integrations/seller/orders/sync")
                            .header("X-Seller-Id", "seller-http-A")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"orderChannel\":\"SHOPIFY\"}"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.savedCount").value(1))
                    .andExpect(jsonPath("$.data.skippedCount").value(0))
                    .andExpect(jsonPath("$.data.orders").isArray())
                    .andExpect(jsonPath("$.data.orders[0].orderId").value("8001"));
        }

        @Test
        @DisplayName("유효한 주문 동기화 요청이 주어지면 주문 동기화를 요청했을 때 DB에 주문을 저장해야 한다")
        void syncOrders_e2e_persistsOrderToDb() throws Exception {
            // given
            ShopifyOrderResponse.OrderNode node = buildShopifyOrderNode(8002L, "#8002", "Bob", "200 Oak Ave");
            node.setLineItems(buildLineItemConnection("SKU-HTTP-01", "Gadget B", 1, null));
            given(channelApiQueryService.findChannelCredential("seller-http-B", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));

            // when
            mockMvc.perform(post("/integrations/seller/orders/sync")
                            .header("X-Seller-Id", "seller-http-B")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"orderChannel\":\"SHOPIFY\"}"))
                    .andExpect(status().isOk());

            // then — DB 직접 조회
            channelOrderRepository.flush();
            ChannelOrder saved = channelOrderRepository.findById("8002").orElseThrow();
            assertThat(saved.getChannelOrderNo()).isEqualTo("#8002");
            assertThat(saved.getItems()).hasSize(1);
            assertThat(saved.getItems().get(0).getId().getSkuId()).isEqualTo("SKU-HTTP-01");
        }

        @Test
        @DisplayName("채널 자격 증명이 없는 판매자 요청이 주어지면 주문 동기화를 요청했을 때 HTTP 404 응답을 반환해야 한다")
        void syncOrders_e2e_missingCredential_returns404() throws Exception {
            // given — 크리덴셜 조회 시 예외 발생 (등록되지 않은 셀러)
            given(channelApiQueryService.findChannelCredential("seller-no-cred", "SHOPIFY"))
                    .willThrow(new BusinessException(ErrorCode.CHANNEL_CREDENTIALS_NOT_FOUND, "채널 API 정보가 존재하지 않습니다"));

            mockMvc.perform(post("/integrations/seller/orders/sync")
                            .header("X-Seller-Id", "seller-no-cred")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"orderChannel\":\"SHOPIFY\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("INT-103"));
        }

        @Test
        @DisplayName("판매자 헤더가 없는 요청이 주어지면 주문 동기화를 요청했을 때 HTTP 400 응답을 반환해야 한다")
        void syncOrders_e2e_missingSellerIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/integrations/seller/orders/sync")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"orderChannel\":\"SHOPIFY\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("유효한 판매자 헤더와 채널 키가 주어지면 채널 기준 주문 동기화를 요청했을 때 HTTP 200 응답과 처리 건수를 반환해야 한다")
        void syncOrdersByChannel_e2e_returnsHttpOkWithCounts() throws Exception {
            ShopifyOrderResponse.OrderNode node = buildShopifyOrderNode(8101L, "#8101", "Grace", "700 Cedar St");
            given(channelApiQueryService.findChannelCredential("seller-http-channel-A", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/sync", "SHOPIFY")
                            .header("X-Seller-Id", "seller-http-channel-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.savedCount").value(1))
                    .andExpect(jsonPath("$.data.skippedCount").value(0))
                    .andExpect(jsonPath("$.data.orders[0].orderId").value("8101"));
        }

        @Test
        @DisplayName("유효한 판매자 헤더와 채널 키가 주어지면 채널 기준 주문 동기화를 요청했을 때 DB에 주문을 저장해야 한다")
        void syncOrdersByChannel_e2e_persistsOrderToDb() throws Exception {
            ShopifyOrderResponse.OrderNode node = buildShopifyOrderNode(8102L, "#8102", "Henry", "800 Maple Ave");
            node.setLineItems(buildLineItemConnection("SKU-HTTP-02", "Gadget C", 2, null));
            given(channelApiQueryService.findChannelCredential("seller-http-channel-B", "SHOPIFY")).willReturn(buildCredential());
            given(shopifyOrderClient.getOrders(anyString(), anyString())).willReturn(List.of(node));

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/sync", "SHOPIFY")
                            .header("X-Seller-Id", "seller-http-channel-B"))
                    .andExpect(status().isOk());

            channelOrderRepository.flush();
            ChannelOrder saved = channelOrderRepository.findById("8102").orElseThrow();
            assertThat(saved.getChannelOrderNo()).isEqualTo("#8102");
            assertThat(saved.getItems()).hasSize(1);
            assertThat(saved.getItems().get(0).getId().getSkuId()).isEqualTo("SKU-HTTP-02");
        }
    }

    /* ===================================================================
     * 5) ChannelApiRepository — 전체 흐름 (저장 + 조회)
     * =================================================================== */

    @Nested
    @DisplayName("채널 API 저장 및 조회 통합 테스트")
    class ChannelApiIntegrationTests {

        // 채널 연결 정보의 저장과 sellerId 기반 조회를 함께 검증한다.
        @Test
        @DisplayName("채널 API 정보를 저장하면 판매자 ID로 조회했을 때 저장한 채널 정보를 반환해야 한다")
        void saveAndFindChannelApi() {
            // given
            ChannelApiId id = new ChannelApiId("seller-intg", "SHOPIFY");
            channelApiRepository.saveAndFlush(
                    ChannelApi.builder().id(id).channelApi("shopify-api-token").build()
            );

            // when
            List<ChannelApi> result = channelApiMapper.findByIdSellerId("seller-intg");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId().getChannelName()).isEqualTo("SHOPIFY");
            assertThat(result.get(0).getChannelApi()).isEqualTo("shopify-api-token");
        }

        @Test
        @DisplayName("채널 API 정보를 저장하면 저장했을 때 createdAt과 updatedAt을 자동으로 기록해야 한다")
        void saveChannelApi_setsAuditFields() {
            ChannelApi saved = channelApiRepository.saveAndFlush(
                    ChannelApi.builder()
                            .id(new ChannelApiId("seller-audit", "SHOPIFY"))
                            .channelApi("audit-token")
                            .storeName("audit-store")
                            .build());

            assertThat(saved.getAudit().getCreatedAt()).isNotNull();
            assertThat(saved.getAudit().getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("기존 채널 API 정보를 수정하면 수정했을 때 updatedAt을 갱신해야 한다")
        void updateChannelApi_updatesUpdatedAt() {
            ChannelApi saved = channelApiRepository.saveAndFlush(
                    ChannelApi.builder()
                            .id(new ChannelApiId("seller-audit-update", "SHOPIFY"))
                            .channelApi("old-token")
                            .storeName("old-store")
                            .build());
            LocalDateTime createdAt = saved.getAudit().getCreatedAt();

            saved.updateConnection("new-token", "new-store");
            ChannelApi updated = channelApiRepository.saveAndFlush(saved);

            assertThat(updated.getAudit().getCreatedAt()).isEqualTo(createdAt);
            assertThat(updated.getAudit().getUpdatedAt()).isNotNull();
            assertThat(updated.getAudit().getUpdatedAt()).isAfterOrEqualTo(createdAt);
        }
    }

    @Nested
    @DisplayName("셀러 채널 연결 HTTP 통합 테스트")
    class ConnectSellerChannelHttpIntegrationTests {

        @Test
        @DisplayName("유효한 Shopify 연결 요청이 주어지면 채널 연결을 요청했을 때 HTTP 200 응답과 저장된 연결 정보를 반환해야 한다")
        void connectSellerChannel_e2e_returnsSavedConnection() throws Exception {
            given(shopifyPingClient.verify("my-shopify-store", "shpat_http_token")).willReturn(true);

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/connect", "SHOPIFY")
                            .header("X-Seller-Id", "seller-http-connect")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "storeName":"my-shopify-store",
                                      "channelApi":"shpat_http_token",
                                      "storeAlias":"Shopify KR Store",
                                      "contactEmail":"ops@example.com",
                                      "syncMode":"AUTO"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.channelName").value("SHOPIFY"))
                    .andExpect(jsonPath("$.data.storeName").value("my-shopify-store"))
                    .andExpect(jsonPath("$.data.channelApi").value("shpat_http_token"));

            ChannelApi saved = channelApiRepository.findById(
                    new ChannelApiId("seller-http-connect", "SHOPIFY")).orElseThrow();
            assertThat(saved.getStoreName()).isEqualTo("my-shopify-store");
            assertThat(saved.getChannelApi()).isEqualTo("shpat_http_token");
        }

        @Test
        @DisplayName("기존 Shopify 연결 정보가 있으면 채널 연결을 다시 요청했을 때 row를 추가하지 않고 갱신해야 한다")
        void connectSellerChannel_e2e_updatesExistingRow() throws Exception {
            channelApiRepository.saveAndFlush(ChannelApi.builder()
                    .id(new ChannelApiId("seller-http-update", "SHOPIFY"))
                    .channelApi("old-token")
                    .storeName("old-store")
                    .build());
            given(shopifyPingClient.verify("new-store", "new-token")).willReturn(true);

            mockMvc.perform(post("/integrations/seller/channels/{channelKey}/connect", "SHOPIFY")
                            .header("X-Seller-Id", "seller-http-update")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("""
                                    {"storeName":"new-store","channelApi":"new-token"}
                                    """))
                    .andExpect(status().isOk());

            List<ChannelApi> result = channelApiMapper.findByIdSellerId("seller-http-update");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStoreName()).isEqualTo("new-store");
            assertThat(result.get(0).getChannelApi()).isEqualTo("new-token");
        }
    }

    @Nested
    @DisplayName("주문 아이템 audit 통합 테스트")
    class ChannelOrderItemAuditIntegrationTests {

        @Test
        @DisplayName("주문 아이템을 저장하면 저장했을 때 createdAt과 updatedAt을 자동으로 기록해야 한다")
        void saveChannelOrderItem_setsAuditFields() {
            ChannelOrder order = ChannelOrder.builder()
                    .orderId("ORDER-ITEM-AUDIT-001")
                    .channelOrderNo("#ITEM-001")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-item-audit")
                    .build();
            ChannelOrderItem item = ChannelOrderItem.builder()
                    .id(new ChannelOrderItemId("ORDER-ITEM-AUDIT-001", "SKU-001"))
                    .channelOrder(order)
                    .quantity(2)
                    .productNameSnapshot("상품 A")
                    .build();
            order.addItem(item);

            ChannelOrder saved = channelOrderRepository.saveAndFlush(order);

            assertThat(saved.getItems()).hasSize(1);
            assertThat(saved.getItems().get(0).getAudit().getCreatedAt()).isNotNull();
            assertThat(saved.getItems().get(0).getAudit().getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("기존 주문 아이템을 수정하면 수정했을 때 updatedAt을 갱신해야 한다")
        void updateChannelOrderItem_updatesUpdatedAt() {
            ChannelOrder order = ChannelOrder.builder()
                    .orderId("ORDER-ITEM-AUDIT-002")
                    .channelOrderNo("#ITEM-002")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-item-audit")
                    .build();
            ChannelOrderItem item = ChannelOrderItem.builder()
                    .id(new ChannelOrderItemId("ORDER-ITEM-AUDIT-002", "SKU-002"))
                    .channelOrder(order)
                    .quantity(1)
                    .productNameSnapshot("상품 B")
                    .build();
            order.addItem(item);
            channelOrderRepository.saveAndFlush(order);

            ChannelOrder saved = channelOrderRepository.findById("ORDER-ITEM-AUDIT-002").orElseThrow();
            ChannelOrderItem savedItem = saved.getItems().get(0);
            LocalDateTime createdAt = savedItem.getAudit().getCreatedAt();

            savedItem.updateProductNameSnapshot("상품 B 수정");
            channelOrderRepository.saveAndFlush(saved);

            assertThat(savedItem.getAudit().getCreatedAt()).isEqualTo(createdAt);
            assertThat(savedItem.getAudit().getUpdatedAt()).isNotNull();
            assertThat(savedItem.getAudit().getUpdatedAt()).isAfterOrEqualTo(createdAt);
        }
    }

    /* ===================================================================
     * 헬퍼 메서드
     * =================================================================== */

    private ChannelCredential buildCredential() {
        ChannelCredential cred = new ChannelCredential();
        cred.setStoreName("test-store");
        cred.setAccessToken("test-token");
        return cred;
    }

    /**
     * ShopifyOrderResponse.OrderNode 테스트 픽스처 생성
     */
    private ShopifyOrderResponse.OrderNode buildShopifyOrderNode(long id, String name, String receiverName, String address) {
        // Sync service가 참조하는 최소 필드만 채운다.
        ShopifyOrderResponse.OrderNode node = new ShopifyOrderResponse.OrderNode();
        node.setId("gid://shopify/Order/" + id);
        node.setName(name);
        node.setCreatedAt("2024-01-15T10:00:00+09:00");

        ShopifyOrderResponse.ShippingAddress addr = new ShopifyOrderResponse.ShippingAddress();
        addr.setName(receiverName);
        addr.setAddress1(address);
        addr.setCity("New York");
        addr.setProvinceCode("NY");
        addr.setZip("10001");
        node.setShippingAddress(addr);

        return node;
    }

    /**
     * ShopifyOrderResponse.LineItemConnection 테스트 픽스처 생성
     */
    private ShopifyOrderResponse.LineItemConnection buildLineItemConnection(
            String sku, String title, int quantity, String gidVariantId) {

        ShopifyOrderResponse.LineItemNode lineItemNode = new ShopifyOrderResponse.LineItemNode();
        lineItemNode.setSku(sku);
        lineItemNode.setTitle(title);
        lineItemNode.setQuantity(quantity);

        if (gidVariantId != null) {
            ShopifyOrderResponse.VariantNode variantNode = new ShopifyOrderResponse.VariantNode();
            variantNode.setId(gidVariantId);
            lineItemNode.setVariant(variantNode);
        }

        ShopifyOrderResponse.LineItemEdge edge = new ShopifyOrderResponse.LineItemEdge();
        edge.setNode(lineItemNode);

        ShopifyOrderResponse.LineItemConnection connection = new ShopifyOrderResponse.LineItemConnection();
        connection.setEdges(List.of(edge));
        return connection;
    }
}


