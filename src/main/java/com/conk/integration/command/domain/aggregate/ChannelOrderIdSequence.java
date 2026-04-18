package com.conk.integration.command.domain.aggregate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/*
 * 날짜별 채널 주문 ID 시퀀스 테이블.
 * 채널 구분 없이 날짜 단위로 공유하며 하루 최대 99999건을 채번한다.
 * 물리 테이블: channel_order_id_sequence
 */
@Entity
@Table(name = "channel_order_id_sequence")
public class ChannelOrderIdSequence {

    @Id
    @Column(name = "seq_date")
    private LocalDate seqDate;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq;

    protected ChannelOrderIdSequence() {}

    public static ChannelOrderIdSequence of(LocalDate date) {
        ChannelOrderIdSequence seq = new ChannelOrderIdSequence();
        seq.seqDate = date;
        seq.lastSeq = 0;
        return seq;
    }

    public int increment() {
        if (lastSeq >= 99999) {
            throw new IllegalStateException("일일 채널 주문 ID 시퀀스가 최대값(99999)에 도달했습니다.");
        }
        return ++lastSeq;
    }
}
