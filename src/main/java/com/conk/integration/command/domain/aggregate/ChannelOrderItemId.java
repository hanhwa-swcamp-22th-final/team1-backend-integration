package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

// 주문 아이템을 orderId + skuId로 식별하는 복합 키다.
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Getter
public class ChannelOrderItemId implements Serializable {

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "sku_id")
    private String skuId;
}
