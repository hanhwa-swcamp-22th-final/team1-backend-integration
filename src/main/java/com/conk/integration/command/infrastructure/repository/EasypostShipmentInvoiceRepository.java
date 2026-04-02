package com.conk.integration.command.infrastructure.repository;

import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

// 구매된 EasyPost 송장 엔티티를 저장/조회한다.
public interface EasypostShipmentInvoiceRepository extends JpaRepository<EasypostShipmentInvoice, String> {
}
