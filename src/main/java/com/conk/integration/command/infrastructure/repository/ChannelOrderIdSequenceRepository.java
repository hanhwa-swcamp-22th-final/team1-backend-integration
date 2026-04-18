package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.ChannelOrderIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ChannelOrderIdSequenceRepository extends JpaRepository<ChannelOrderIdSequence, LocalDate> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ChannelOrderIdSequence s WHERE s.seqDate = :date")
    Optional<ChannelOrderIdSequence> findBySeqDateForUpdate(@Param("date") LocalDate date);
}
