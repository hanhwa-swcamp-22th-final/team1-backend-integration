package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
    @Builder.Default
    private AuditFields audit = new AuditFields();

    public void updateProductNameSnapshot(String productNameSnapshot) {
        this.productNameSnapshot = productNameSnapshot;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        audit.setCreatedAt(now);
        audit.setUpdatedAt(now);
    }

    @PreUpdate
    protected void onUpdate() {
        audit.setUpdatedAt(LocalDateTime.now());
    }
}
