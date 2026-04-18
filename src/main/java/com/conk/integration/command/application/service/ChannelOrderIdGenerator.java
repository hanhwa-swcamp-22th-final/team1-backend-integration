package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrderIdSequence;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.command.infrastructure.repository.ChannelOrderIdSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/*
 * 채널 주문 ID 채번 서비스.
 *
 * 형식: ORD-{년도}-{월일}-{시퀀스 5자리}-{채널명}
 * 예시: ORD-2026-0418-00001-SHOPIFY
 *
 * 날짜별 시퀀스는 채널 구분 없이 공유되며 1부터 시작해 99999까지 증가한다.
 * 날짜가 바뀌면 시퀀스가 1부터 다시 시작한다.
 * 동시성은 DB 비관적 락(PESSIMISTIC_WRITE)으로 보장한다.
 */
@Service
@RequiredArgsConstructor
public class ChannelOrderIdGenerator {

    private final ChannelOrderIdSequenceRepository sequenceRepository;

    @Transactional
    public String generate(OrderChannel channel) {
        LocalDate today = LocalDate.now();

        ChannelOrderIdSequence sequence = sequenceRepository.findBySeqDateForUpdate(today)
                .orElseGet(() -> sequenceRepository.save(ChannelOrderIdSequence.of(today)));

        int seq = sequence.increment();

        return String.format("ORD-%d-%02d%02d-%05d-%s",
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                seq,
                channel.name());
    }
}
