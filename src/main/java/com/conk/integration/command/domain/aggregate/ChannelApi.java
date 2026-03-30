package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
