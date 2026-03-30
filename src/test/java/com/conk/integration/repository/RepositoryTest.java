package com.conk.integration.repository;

import com.conk.integration.command.domain.aggregate.*;
import com.conk.integration.command.domain.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * [2단계] Repository 테스트
 *
 * - @DataJpaTest : JPA 관련 컴포넌트만 로드하는 슬라이스 테스트.
 *   H2 인메모리 DB를 사용하고, 각 테스트 후 자동 롤백합니다.
 * - save() / findById() 같은 기본 CRUD는 검증 제외하고,
 *   커스텀 쿼리 메서드만 집중 검증합니다.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("[Repository] JPA Repository 슬라이스 테스트")
class RepositoryTest {

    @Autowired
    private ChannelOrderRepository channelOrderRepository;

    @Autowired
    private ChannelApiRepository channelApiRepository;

    @Autowired
    private EasypostShipmentInvoiceRepository invoiceRepository;

    /* ===================================================================
     * ChannelOrderRepository
     * =================================================================== */

    @Nested
    @DisplayName("ChannelOrderRepository — 커스텀 쿼리 검증")
    class ChannelOrderRepositoryTests {

        @Test
        @DisplayName("findBySellerId() — 해당 sellerId의 주문만 반환한다")
        void findBySellerId_returnsOnlyMatchingSeller() {
            // given
            ChannelOrder orderA1 = ChannelOrder.builder()
                    .orderId("O-A-001").channelOrderNo("#A-001")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .orderedAt(LocalDateTime.now())
                    .sellerId("seller-A").build();
            ChannelOrder orderA2 = ChannelOrder.builder()
                    .orderId("O-A-002").channelOrderNo("#A-002")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .orderedAt(LocalDateTime.now())
                    .sellerId("seller-A").build();
            ChannelOrder orderB = ChannelOrder.builder()
                    .orderId("O-B-001").channelOrderNo("#B-001")
                    .orderChannel(OrderChannel.SHOPIFY)
                    .orderedAt(LocalDateTime.now())
                    .sellerId("seller-B").build();

            channelOrderRepository.saveAll(List.of(orderA1, orderA2, orderB));

            // when
            List<ChannelOrder> result = channelOrderRepository.findBySellerId("seller-A");

            // then
            assertThat(result).hasSize(2)
                    .extracting(ChannelOrder::getSellerId)
                    .containsOnly("seller-A");
        }

        @Test
        @DisplayName("findBySellerId() — 해당 sellerId의 주문이 없으면 빈 리스트를 반환한다")
        void findBySellerId_returnsEmpty_whenNoMatch() {
            // given — seller-A 주문만 존재
            channelOrderRepository.save(
                    ChannelOrder.builder()
                            .orderId("O-A-001").channelOrderNo("#A-001")
                            .orderChannel(OrderChannel.SHOPIFY)
                            .sellerId("seller-A").build()
            );

            // when
            List<ChannelOrder> result = channelOrderRepository.findBySellerId("seller-NONE");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("existsById() — 저장된 주문 ID로 조회 시 true를 반환한다")
        void existsById_returnsTrueForSavedOrder() {
            // given
            channelOrderRepository.save(
                    ChannelOrder.builder()
                            .orderId("O-EXISTS-001").channelOrderNo("#X-001")
                            .orderChannel(OrderChannel.SHOPIFY)
                            .sellerId("seller-X").build()
            );

            // when & then
            assertThat(channelOrderRepository.existsById("O-EXISTS-001")).isTrue();
            assertThat(channelOrderRepository.existsById("O-NOT-EXIST")).isFalse();
        }
    }

    /* ===================================================================
     * ChannelApiRepository
     * =================================================================== */

    @Nested
    @DisplayName("ChannelApiRepository — 커스텀 쿼리 검증")
    class ChannelApiRepositoryTests {

        @Test
        @DisplayName("findByIdSellerId() — 해당 sellerId의 채널 API 목록을 반환한다")
        void findByIdSellerId_returnsMatchingChannelApis() {
            // given
            ChannelApiId idShopify = new ChannelApiId("seller-A", "SHOPIFY");
            ChannelApiId idAmazon  = new ChannelApiId("seller-A", "AMAZON");
            ChannelApiId idOther   = new ChannelApiId("seller-B", "SHOPIFY");

            channelApiRepository.saveAll(List.of(
                    ChannelApi.builder().id(idShopify).channelApi("shopify-token-A").build(),
                    ChannelApi.builder().id(idAmazon).channelApi("amazon-token-A").build(),
                    ChannelApi.builder().id(idOther).channelApi("shopify-token-B").build()
            ));

            // when
            List<ChannelApi> result = channelApiRepository.findByIdSellerId("seller-A");

            // then
            assertThat(result).hasSize(2)
                    .extracting(api -> api.getId().getSellerId())
                    .containsOnly("seller-A");
        }

        @Test
        @DisplayName("findByIdSellerId() — 존재하지 않는 sellerId이면 빈 리스트를 반환한다")
        void findByIdSellerId_returnsEmpty_whenNoMatch() {
            // when
            List<ChannelApi> result = channelApiRepository.findByIdSellerId("seller-NONE");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("복합 키(ChannelApiId)로 findById() 조회 시 올바른 엔티티가 반환된다")
        void findById_withCompositeKey_returnsCorrectEntity() {
            // given
            ChannelApiId id = new ChannelApiId("seller-C", "SHOPIFY");
            channelApiRepository.save(
                    ChannelApi.builder().id(id).channelApi("shopify-api-key").build()
            );

            // when
            Optional<ChannelApi> found = channelApiRepository.findById(id);

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getChannelApi()).isEqualTo("shopify-api-key");
        }
    }

    /* ===================================================================
     * EasypostShipmentInvoiceRepository
     * =================================================================== */

    @Nested
    @DisplayName("EasypostShipmentInvoiceRepository — CRUD 확인")
    class InvoiceRepositoryTests {

        @Test
        @DisplayName("Invoice를 저장하고 invoiceNo로 조회하면 동일한 데이터가 반환된다")
        void save_and_findById_returnsSameInvoice() {
            // given
            EasypostShipmentInvoice invoice = EasypostShipmentInvoice.builder()
                    .invoiceNo("INV-TEST-001")
                    .carrierType(CarrierType.USPS)
                    .freightChargeAmt(2500)
                    .shipToAddress("New York, NY 10001, US")
                    .trackingUrl("https://track.easypost.com/TEST001")
                    .labelFileUrl("https://cdn.easypost.com/label/TEST001.pdf")
                    .build();

            invoiceRepository.save(invoice);

            // when
            Optional<EasypostShipmentInvoice> found = invoiceRepository.findById("INV-TEST-001");

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getCarrierType()).isEqualTo(CarrierType.USPS);
            assertThat(found.get().getFreightChargeAmt()).isEqualTo(2500);
        }

        @Test
        @DisplayName("존재하지 않는 invoiceNo로 조회하면 Optional.empty()가 반환된다")
        void findById_notExisting_returnsEmpty() {
            Optional<EasypostShipmentInvoice> result = invoiceRepository.findById("NOT-EXIST");
            assertThat(result).isEmpty();
        }
    }
}
