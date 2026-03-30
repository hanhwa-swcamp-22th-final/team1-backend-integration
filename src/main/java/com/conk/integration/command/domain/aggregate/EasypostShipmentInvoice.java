package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// EasyPost에서 구매한 배송 라벨/추적 정보를 내부 송장으로 저장한다.
@Entity
@Table(name = "easypost_shipment_invoice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@AllArgsConstructor
public class EasypostShipmentInvoice {

    @Id
    @Column(name = "invoice_no")
    private String invoiceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier_type", length = 50)
    private CarrierType carrierType;

    @Column(name = "freight_charge_amt")
    private Integer freightChargeAmt;

    @Column(name = "ship_to_address")
    private String shipToAddress;

    @Column(name = "tracking_url")
    private String trackingUrl;

    @Column(name = "label_file_url")
    private String labelFileUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    // schema: VARCHAR(255) — not DATETIME
    @Column(name = "issued_at")
    private String issuedAt;

    // schema: VARCHAR(255) — not DATETIME
    @Column(name = "handover_at")
    private String handoverAt;

    // 생성 시 감사 시각을 자동으로 기록한다.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // 수정 시 updatedAt만 갱신한다.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
