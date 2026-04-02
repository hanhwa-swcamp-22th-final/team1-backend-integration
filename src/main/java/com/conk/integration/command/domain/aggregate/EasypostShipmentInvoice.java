package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// EasyPost에서 구매한 배송 라벨/추적 정보를 내부 송장으로 저장한다.
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@AllArgsConstructor
public class EasypostShipmentInvoice {

    @Id
    private String invoiceNo;

    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private CarrierType carrierType;

    private Integer freightChargeAmt;

    private String shipToAddress;

    private String trackingUrl;

    private String labelFileUrl;

    @Embedded
    @Builder.Default
    private AuditFields audit = new AuditFields();

    // schema: VARCHAR(255) — not DATETIME
    private String issuedAt;

    // schema: VARCHAR(255) — not DATETIME
    private String handoverAt;

    // 생성 시 감사 시각을 자동으로 기록한다.
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        audit.setCreatedAt(now);
        audit.setUpdatedAt(now);
    }

    // 수정 시 updatedAt만 갱신한다.
    @PreUpdate
    protected void onUpdate() {
        audit.setUpdatedAt(LocalDateTime.now());
    }
}
