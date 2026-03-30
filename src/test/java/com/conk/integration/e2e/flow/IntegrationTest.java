package com.conk.integration.e2e.flow;

import com.conk.integration.command.domain.aggregate.*;
import com.conk.integration.command.domain.repository.*;
import com.conk.integration.command.application.service.ShopifyFulfillmentService;
import com.conk.integration.command.application.service.ShopifyOrderSyncService;
import com.conk.integration.command.infrastructure.service.ShopifyApiClient;
import com.conk.integration.command.infrastructure.service.ShopifyFulfillmentApiClient;
import com.conk.integration.command.application.dto.response.ShopifyOrderDto;
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
 *   - ShopifyFulfillmentService.fulfill() : DB 조회 → Shopify API 호출 전체 흐름
 *   - GET /integrations/seller/orders : Controller → Service → DB → HTTP 응답 전체 흐름
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("[통합 테스트] 전체 시스템 데이터 흐름 검증 (Controller → Service → DB)")
class IntegrationTest {

    /* ---------- MockMvc (HTTP 계층 테스트) ---------- */
    @Autowired
    private MockMvc mockMvc;

    /* ---------- 외부 API — MockitoBean으로만 격리 ---------- */
    @MockitoBean
    private ShopifyApiClient shopifyApiClient;

    @MockitoBean
    private ShopifyFulfillmentApiClient shopifyFulfillmentApiClient;

    /* ---------- 실제 Bean ---------- */
    @Autowired
    private ShopifyOrderSyncService shopifyOrderSyncService;

    @Autowired
    private ShopifyFulfillmentService shopifyFulfillmentService;

    @Autowired
    private ChannelOrderRepository channelOrderRepository;

    @Autowired
    private EasypostShipmentInvoiceRepository invoiceRepository;

    @Autowired
    private ChannelApiRepository channelApiRepository;

    /* ===================================================================
     * 1) ShopifyOrderSyncService — 전체 흐름 (API → DB 저장)
     * =================================================================== */

    @Nested
    @DisplayName("ShopifyOrderSyncService 통합 — Shopify API 응답이 DB에 저장된다")
    class SyncOrdersIntegrationTests {

        @Test
        @DisplayName("syncOrders() — Shopify 주문 2건 반환 시 DB에 2건이 저장된다")
        void syncOrders_persistsTwoOrders() {
            // given
            ShopifyOrderDto dto1 = buildShopifyOrderDto(9001L, "#9001", "Alice", "100 Main St");
            ShopifyOrderDto dto2 = buildShopifyOrderDto(9002L, "#9002", "Bob",   "200 Oak Ave");

            given(shopifyApiClient.getOrders()).willReturn(List.of(dto1, dto2));

            // when
            shopifyOrderSyncService.syncOrders("seller-integration-A");

            // then — DB에서 직접 조회하여 2건 확인
            List<ChannelOrder> saved = channelOrderRepository.findBySellerId("seller-integration-A");
            assertThat(saved).hasSize(2);
            assertThat(saved).extracting(ChannelOrder::getChannelOrderNo)
                    .containsExactlyInAnyOrder("#9001", "#9002");
        }

        @Test
        @DisplayName("syncOrders() — 이미 저장된 주문은 중복 저장되지 않는다 (멱등성)")
        void syncOrders_idempotent_doesNotDuplicateExistingOrder() {
            // given — 주문 9003은 이미 DB에 존재
            channelOrderRepository.save(ChannelOrder.builder()
                    .orderId("9003").channelOrderNo("#9003")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .sellerId("seller-integration-B").build());

            // Shopify API는 동일한 주문을 다시 반환
            ShopifyOrderDto existingDto = buildShopifyOrderDto(9003L, "#9003", "Charlie", "300 Pine Rd");
            given(shopifyApiClient.getOrders()).willReturn(List.of(existingDto));

            // when
            shopifyOrderSyncService.syncOrders("seller-integration-B");

            // then — 여전히 1건만 존재 (중복 저장 없음)
            List<ChannelOrder> result = channelOrderRepository.findBySellerId("seller-integration-B");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("syncOrders() — Shopify API 주문이 0건이면 DB에 아무 것도 저장되지 않는다")
        void syncOrders_emptyResponse_savesNothing() {
            // given
            given(shopifyApiClient.getOrders()).willReturn(List.of());

            // when
            shopifyOrderSyncService.syncOrders("seller-integration-C");

            // then
            List<ChannelOrder> result = channelOrderRepository.findBySellerId("seller-integration-C");
            assertThat(result).isEmpty();
        }
    }

    /* ===================================================================
     * 2) ShopifyFulfillmentService — 전체 흐름 (DB 조회 → Shopify API 호출)
     * =================================================================== */

    @Nested
    @DisplayName("ShopifyFulfillmentService 통합 — DB 조회 후 Shopify API를 호출한다")
    class FulfillmentIntegrationTests {

        @Test
        @DisplayName("fulfill() — 주문과 invoice가 DB에 있으면 Shopify fulfillment API가 호출된다")
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

            // when
            shopifyFulfillmentService.fulfill("ORDER-INTG-001");

            // then — Shopify fulfillment API가 실제로 호출되었는지 검증
            then(shopifyFulfillmentApiClient).should(times(1))
                    .createFulfillment(eq("#INTG-001"), any());
        }

        @Test
        @DisplayName("fulfill() — DB에 주문이 없으면 IllegalArgumentException이 발생한다")
        void fulfill_throwsWhenOrderNotInDb() {
            assertThatThrownBy(() -> shopifyFulfillmentService.fulfill("ORDER-NOT-EXIST"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ChannelOrder를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("fulfill() — 주문에 invoiceNo가 없으면 IllegalStateException이 발생한다")
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
            assertThatThrownBy(() -> shopifyFulfillmentService.fulfill("ORDER-NO-INV"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("송장이 발급되지 않은 주문");
        }
    }

    /* ===================================================================
     * 3) HTTP → Controller → Service → DB 전체 흐름
     * =================================================================== */

    @Nested
    @DisplayName("GET /integrations/seller/orders — HTTP 전체 흐름 통합 테스트")
    class OrderQueryHttpIntegrationTests {

        @Test
        @DisplayName("DB에 주문이 있을 때 HTTP 요청으로 조회하면 200과 주문 데이터가 반환된다")
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
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id").value("E2E-ORD-001"))
                    .andExpect(jsonPath("$.data[0].channel").value("SHOPIFY"))
                    .andExpect(jsonPath("$.data[0].recipient").value("E2E 수신자"))
                    .andExpect(jsonPath("$.data[0].status").value("NEW"));
        }

        @Test
        @DisplayName("DB에 주문이 없을 때 HTTP 요청으로 조회하면 200과 빈 배열이 반환된다")
        void getOrders_e2e_returnsEmptyWhenNoData() throws Exception {
            // when & then — 데이터 없는 sellerId로 조회
            mockMvc.perform(get("/integrations/seller/orders")
                            .header("X-Seller-Id", "seller-EMPTY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("X-Seller-Id 헤더 누락 시 HTTP 400이 반환된다")
        void getOrders_e2e_missingHeader_returns400() throws Exception {
            mockMvc.perform(get("/integrations/seller/orders"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("invoiceNo가 있는 주문 조회 시 status가 PROCESSING으로 반환된다")
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
                    .andExpect(jsonPath("$.data[0].status").value("PROCESSING"));
        }
    }

    /* ===================================================================
     * 4) ChannelApiRepository — 전체 흐름 (저장 + 조회)
     * =================================================================== */

    @Nested
    @DisplayName("ChannelApi DB 저장 및 조회 통합 테스트")
    class ChannelApiIntegrationTests {

        @Test
        @DisplayName("ChannelApi 저장 후 findByIdSellerId()로 조회하면 정상적으로 반환된다")
        void saveAndFindChannelApi() {
            // given
            ChannelApiId id = new ChannelApiId("seller-intg", "SHOPIFY");
            channelApiRepository.save(
                    ChannelApi.builder().id(id).channelApi("shopify-api-token").build()
            );

            // when
            List<ChannelApi> result = channelApiRepository.findByIdSellerId("seller-intg");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId().getChannelName()).isEqualTo("SHOPIFY");
            assertThat(result.get(0).getChannelApi()).isEqualTo("shopify-api-token");
        }
    }

    /* ===================================================================
     * 헬퍼 메서드
     * =================================================================== */

    /**
     * ShopifyOrderDto 테스트 픽스처 생성
     */
    private ShopifyOrderDto buildShopifyOrderDto(long id, String name, String receiverName, String address) {
        ShopifyOrderDto dto = new ShopifyOrderDto();
        dto.setId(id);
        dto.setName(name);
        dto.setCreatedAt("2024-01-15T10:00:00+09:00");

        ShopifyOrderDto.ShippingAddress addr = new ShopifyOrderDto.ShippingAddress();
        addr.setName(receiverName);
        addr.setAddress1(address);
        addr.setCity("New York");
        addr.setProvinceCode("NY");
        addr.setZip("10001");
        dto.setShippingAddress(addr);

        return dto;
    }
}
