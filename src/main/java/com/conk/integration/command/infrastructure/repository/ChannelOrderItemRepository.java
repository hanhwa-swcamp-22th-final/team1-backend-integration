package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.aggregate.embeddable.ChannelOrderItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelOrderItemRepository extends JpaRepository<ChannelOrderItem, ChannelOrderItemId> {
}
