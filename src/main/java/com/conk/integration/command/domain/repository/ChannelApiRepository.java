package com.conk.integration.command.domain.repository;

import com.conk.integration.command.domain.aggregate.ChannelApi;
import com.conk.integration.command.domain.aggregate.ChannelApiId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelApiRepository extends JpaRepository<ChannelApi, ChannelApiId> {
}
