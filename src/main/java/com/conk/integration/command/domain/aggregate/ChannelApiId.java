package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

// sellerId + channelName 조합으로 채널 연결을 식별하는 복합 키다.
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Getter
public class ChannelApiId implements Serializable {

    @Column(name = "seller_id")
    private String sellerId;

    @Column(name = "channel_name")
    private String channelName;
}
