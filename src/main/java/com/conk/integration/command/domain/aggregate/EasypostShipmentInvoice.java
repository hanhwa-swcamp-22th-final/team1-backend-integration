package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.*;
import lombok.*;

// EasyPost에서 구매한 배송 라벨/추적 정보를 내부 송장으로 저장한다.
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@AllArgsConstructor
public class EasypostShipmentInvoice {

    @Id
    private String invoiceNo;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private CarrierType carrierType;

    private Integer freightChargeAmt;

    private String shipToAddress;

    private String trackingUrl;

    private String labelFileUrl;

    @Embedded
    private AuditFields audit;

    // schema: VARCHAR(255) — not DATETIME
    private String issuedAt;

    // schema: VARCHAR(255) — not DATETIME
    private String handoverAt;

    // 생성 시 감사 시각을 자동으로 기록한다.
    @PrePersist
    protected void onCreate() {
        if (audit == null) audit = new AuditFields();
        audit.onCreate();
    }

    // 수정 시 updatedAt만 갱신한다.
    @PreUpdate
    protected void onUpdate() {
        if (audit == null) audit = new AuditFields();
        audit.onUpdate();
    }
}
