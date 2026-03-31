package com.conk.integration.command.domain.aggregate;

import com.conk.integration.command.domain.aggregate.embeddable.AuditFields;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import jakarta.persistence.*;
import lombok.*;

// 셀러가 채널별로 연결한 API 자격 정보를 저장한다.
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@AllArgsConstructor
public class ChannelApi {

    @EmbeddedId
    private ChannelApiId id;

    @Column(nullable = false)
    private String channelApi;

    private String storeName;

    @Embedded
    @Builder.Default
    private AuditFields audit = new AuditFields();
}
