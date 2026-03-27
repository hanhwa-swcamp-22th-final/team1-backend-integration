package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

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
