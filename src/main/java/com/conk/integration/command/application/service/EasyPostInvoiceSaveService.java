package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.response.EasyPostShipmentResponse;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.domain.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.service.EasyPostApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// EasyPost shipment 생성 결과를 CONK 송장 엔티티로 변환해 저장한다.
@Service
@RequiredArgsConstructor
public class EasyPostInvoiceSaveService {

    private final EasyPostApiClient easyPostApiClient;
    private final EasypostShipmentInvoiceRepository invoiceRepository;

    /**
     * 배송 송장을 생성하고 DB에 저장한다.
     * 1) shipment 생성 → 2) 최저가 rate 선택 → 3) rate 구매 → 4) DB 저장
     *
     * @param request EasyPost shipment 생성 요청
     * @return 저장된 송장 엔티티
     */
    public EasypostShipmentInvoice createAndSaveInvoice(EasyPostCreateShipmentRequest request) {
        EasyPostShipmentResponse shipment = easyPostApiClient.createShipment(request);

        // EasyPost rate 목록 중 가장 저렴한 운임만 구매 대상으로 선택한다.
        EasyPostShipmentResponse.RateDto cheapest = selectCheapestRate(shipment.getRates());

        EasyPostShipmentResponse bought = easyPostApiClient.buyRate(shipment.getId(), cheapest.getId());

        EasypostShipmentInvoice invoice = toInvoice(bought);
        return invoiceRepository.save(invoice);
    }

    // 유효한 rate 문자열만 대상으로 최저 운임을 계산한다.
    EasyPostShipmentResponse.RateDto selectCheapestRate(List<EasyPostShipmentResponse.RateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            throw new IllegalStateException("운임 정보가 없습니다");
        }
        return rates.stream()
                .filter(r -> r.getRate() != null && isNumeric(r.getRate()))
                .min(Comparator.comparingDouble(r -> Double.parseDouble(r.getRate())))
                .orElseThrow(() -> new IllegalStateException("유효한 운임 정보가 없습니다"));
    }

    // 외부 shipment 응답을 내부 송장 엔티티로 정규화한다.
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
                ? CarrierType.fromEasyPostName(selected.getCarrier())
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

    // tracker 공개 URL이 있으면 우선 사용하고, 없으면 trackingCode 기반 URL을 만든다.
    private String resolveTrackingUrl(EasyPostShipmentResponse response) {
        if (response.getTracker() != null && response.getTracker().getPublicUrl() != null) {
            return response.getTracker().getPublicUrl();
        }
        if (response.getTrackingCode() != null) {
            return "https://track.easypost.com/" + response.getTrackingCode();
        }
        return null;
    }

    // 주소 조각을 사람이 읽을 수 있는 한 줄 문자열로 합친다.
    private String resolveShipToAddress(EasyPostShipmentResponse.AddressDto addr) {
        if (addr == null) return null;
        return Stream.of(addr.getStreet1(), addr.getCity(), addr.getState(), addr.getZip(), addr.getCountry())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    // 운임 문자열이 숫자로 파싱 가능한지 확인한다.
    private boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
