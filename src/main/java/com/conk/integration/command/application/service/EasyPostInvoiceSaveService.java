package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.domain.aggregate.CarrierType;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.service.EasyPostApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class EasyPostInvoiceSaveService {

    private final EasyPostApiClient easyPostApiClient;
    private final EasypostShipmentInvoiceRepository invoiceRepository;

    /**
     * 배송 송장 생성 및 DB 저장
     * 1) shipment 생성 → 2) 최저가 rate 선택 → 3) rate 구매 → 4) DB 저장
     */
    public EasypostShipmentInvoice createAndSaveInvoice(EasyPostCreateShipmentRequest request) {
        EasyPostShipmentResponse shipment = easyPostApiClient.createShipment(request);

        EasyPostShipmentResponse.RateDto cheapest = selectCheapestRate(shipment.getRates());

        EasyPostShipmentResponse bought = easyPostApiClient.buyRate(shipment.getId(), cheapest.getId());

        EasypostShipmentInvoice invoice = toInvoice(bought);
        return invoiceRepository.save(invoice);
    }

    EasyPostShipmentResponse.RateDto selectCheapestRate(List<EasyPostShipmentResponse.RateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            throw new IllegalStateException("No rates available for shipment");
        }
        return rates.stream()
                .filter(r -> r.getRate() != null && isNumeric(r.getRate()))
                .min(Comparator.comparingDouble(r -> Double.parseDouble(r.getRate())))
                .orElseThrow(() -> new IllegalStateException("No valid rates available for shipment"));
    }

    private EasypostShipmentInvoice toInvoice(EasyPostShipmentResponse response) {
        EasyPostShipmentResponse.RateDto selected = response.getSelectedRate();
        String labelUrl = response.getPostageLabel() != null ? response.getPostageLabel().getLabelUrl() : null;
        String trackingUrl = resolveTrackingUrl(response);
        String shipToAddress = resolveShipToAddress(response.getToAddress());

        int freightChargeAmtCents = 0;
        if (selected != null && selected.getRate() != null && isNumeric(selected.getRate())) {
            freightChargeAmtCents = (int) Math.round(Double.parseDouble(selected.getRate()) * 100);
        }

        CarrierType carrierType = selected != null
                ? resolveCarrierType(selected.getCarrier())
                : CarrierType.USPS;

        return EasypostShipmentInvoice.builder()
                .invoiceNo(response.getId())
                .carrierType(carrierType)
                .freightChargeAmt(freightChargeAmtCents)
                .shipToAddress(shipToAddress)
                .trackingUrl(trackingUrl)
                .labelFileUrl(labelUrl)
                .build();
    }

    CarrierType resolveCarrierType(String carrier) {
        if (carrier == null) return CarrierType.USPS;
        return switch (carrier.toUpperCase()) {
            case "UPS" -> CarrierType.UPS;
            case "FEDEX" -> CarrierType.FEDEX;
            default -> CarrierType.USPS;
        };
    }

    private String resolveTrackingUrl(EasyPostShipmentResponse response) {
        if (response.getTracker() != null && response.getTracker().getPublicUrl() != null) {
            return response.getTracker().getPublicUrl();
        }
        if (response.getTrackingCode() != null) {
            return "https://track.easypost.com/" + response.getTrackingCode();
        }
        return null;
    }

    private String resolveShipToAddress(EasyPostShipmentResponse.AddressDto addr) {
        if (addr == null) return null;
        return Stream.of(addr.getStreet1(), addr.getCity(), addr.getState(), addr.getZip(), addr.getCountry())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    private boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
