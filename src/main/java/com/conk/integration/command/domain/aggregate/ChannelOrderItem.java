package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 주문에 속한 SKU 단위 수량/작업 상태를 저장하는 자식 엔티티다.
@Entity
@Table(name = "channel_order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@AllArgsConstructor
public class ChannelOrderItem {

    @EmbeddedId
    private ChannelOrderItemId id;

    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private ChannelOrder channelOrder;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "product_name_snapshot")
    private String productNameSnapshot;

    @Builder.Default
    @Column(name = "picked_quantity")
    private int pickedQuantity = 0;

    @Builder.Default
    @Column(name = "packed_quantity")
    private int packedQuantity = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;
}
