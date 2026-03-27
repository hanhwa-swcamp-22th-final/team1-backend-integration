package com.conk.integration.command.domain.repository;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {

    List<ChannelOrder> findBySellerId(String sellerId);
}
