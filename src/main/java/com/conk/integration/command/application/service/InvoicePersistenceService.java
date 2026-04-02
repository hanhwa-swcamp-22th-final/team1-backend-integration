package com.conk.integration.command.application.service;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.infrastructure.repository.ChannelOrderRepository;
import com.conk.integration.command.infrastructure.repository.EasypostShipmentInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// buyRate 이후 invoice 저장과 ChannelOrder.invoiceNo 업데이트를 담당한다.
// REQUIRES_NEW self-invocation 문제를 방지하기 위해 별도 Bean으로 분리했다.
@Service
@RequiredArgsConstructor
public class InvoicePersistenceService {

    private final EasypostShipmentInvoiceRepository invoiceRepository;
    private final ChannelOrderRepository channelOrderRepository;

    /**
     * buyRate 결과를 DB에 저장하고 주문에 invoiceNo를 반영한다.
     * REQUIRES_NEW: 외부 트랜잭션 롤백과 무관하게 반드시 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EasypostShipmentInvoice saveInvoiceAndAssign(EasypostShipmentInvoice invoice, ChannelOrder order) {
        EasypostShipmentInvoice saved = invoiceRepository.save(invoice);
        order.assignInvoice(saved.getInvoiceNo());
        channelOrderRepository.save(order);
        return saved;
    }
}
