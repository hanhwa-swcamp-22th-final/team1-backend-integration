package com.conk.integration.command.domain.repository;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.ChannelApiId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 채널 연결 정보 조회/저장을 담당하는 JPA 리포지토리다.
public interface ChannelApiRepository extends JpaRepository<ChannelApi, ChannelApiId> {

    /** 특정 셀러의 모든 채널 API 설정 조회 */
    List<ChannelApi> findByIdSellerId(String sellerId);
}
