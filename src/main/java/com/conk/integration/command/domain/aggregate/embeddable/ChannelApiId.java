package com.conk.integration.command.domain.aggregate.embeddable;

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

    private String sellerId;

    private String channelName;
}
