package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.*;
import lombok.*;

// 주문에 속한 SKU 단위 수량/작업 상태를 저장하는 자식 엔티티다.
@Entity
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

    @Column(nullable = false)
    private int quantity;

    private String productNameSnapshot;

    @Builder.Default
    private int pickedQuantity = 0;

    @Builder.Default
    private int packedQuantity = 0;

    @Embedded
    private AuditFields audit;
}
