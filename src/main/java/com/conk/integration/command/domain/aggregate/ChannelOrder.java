package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 외부 채널 주문을 내부 표준 포맷으로 저장하는 주문 aggregate 루트다.
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@AllArgsConstructor
public class ChannelOrder {

    @Id
    private String orderId;

    private String channelOrderNo;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private OrderChannel orderChannel;

    private LocalDateTime orderedAt;

    private String receiverName;

    private String receiverPhoneNo;

    private String shipToAddress1;

    private String shipToAddress2;

    private String shipToState;

    private String shipToCity;

    private String shipToZipCode;

    // schema: VARCHAR(255) — not DATETIME
    private String shippedAt;

    @Column(nullable = false)
    private String sellerId;

    // Cross-aggregate reference: invoice FK as plain String (no @ManyToOne)
    private String invoiceNo;

    // 채널 fulfillment order ID (e.g. Shopify FulfillmentOrder GID)
    private String fulfillmentOrderId;

    // 채널에 송장(운송장번호)이 반영되었는지 여부
    @Column(nullable = false)
    @Builder.Default
    private boolean channelSyncYn = false;

    @Embedded
    @Builder.Default
    private AuditFields audit = new AuditFields();

    @Builder.Default
    @OneToMany(mappedBy = "channelOrder",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<ChannelOrderItem> items = new ArrayList<>();

    // 연관 아이템 컬렉션에 항목을 추가하는 최소 편의 메서드다.
    public void addItem(ChannelOrderItem item) {
        items.add(item);
    }

    // 생성 시 감사 시각을 자동으로 채운다.
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
