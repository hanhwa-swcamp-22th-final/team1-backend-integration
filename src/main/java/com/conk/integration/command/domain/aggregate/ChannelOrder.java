package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 외부 채널 주문을 내부 표준 포맷으로 저장하는 주문 aggregate 루트다.
@Entity
@Table(name = "channel_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@AllArgsConstructor
public class ChannelOrder {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "channel_order_no")
    private String channelOrderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_channel", length = 50)
    private OrderChannel orderChannel;

    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_phone_no")
    private String receiverPhoneNo;

    @Column(name = "ship_to_address1")
    private String shipToAddress1;

    @Column(name = "ship_to_address2")
    private String shipToAddress2;

    @Column(name = "ship_to_state")
    private String shipToState;

    @Column(name = "ship_to_city")
    private String shipToCity;

    @Column(name = "ship_to_zip_code")
    private String shipToZipCode;

    // schema: VARCHAR(255) — not DATETIME
    @Column(name = "shipped_at")
    private String shippedAt;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    // Cross-aggregate reference: invoice FK as plain String (no @ManyToOne)
    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

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
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // 수정 시 updatedAt만 갱신한다.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
