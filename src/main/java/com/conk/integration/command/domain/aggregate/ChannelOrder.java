package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    public void addItem(ChannelOrderItem item) {
        items.add(item);
    }
}
