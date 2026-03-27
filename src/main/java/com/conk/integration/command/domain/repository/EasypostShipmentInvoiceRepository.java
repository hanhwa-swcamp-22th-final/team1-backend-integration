package com.conk.integration.command.domain.repository;

import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasypostShipmentInvoiceRepository extends JpaRepository<EasypostShipmentInvoice, String> {
}
