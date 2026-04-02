package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelApiId;
import org.springframework.data.jpa.repository.JpaRepository;

// 채널 연결 정보 CUD를 담당하는 JPA 리포지토리다.
public interface ChannelApiRepository extends JpaRepository<ChannelApi, ChannelApiId> {
}
