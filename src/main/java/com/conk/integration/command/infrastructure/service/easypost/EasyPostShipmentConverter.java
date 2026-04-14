package com.conk.integration.command.infrastructure.service.easypost;

import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * EasyPost API 응답을 내부 도메인 엔티티로 변환하는 유틸이다.
 */
public final class EasyPostShipmentConverter {

    private EasyPostShipmentConverter() {
    }

    /**
     * 외부 shipment 응답을 내부 송장 엔티티로 정규화한다.
     *
     * @param response EasyPost buyRate 응답
     * @return 저장 가능한 송장 엔티티
     */
    public static EasypostShipmentInvoice toInvoice(EasyPostShipmentResponse response) {
        return toInvoice(response, null, null);
    }

    public static EasypostShipmentInvoice toInvoice(EasyPostShipmentResponse response, String orderId, String requestedService) {
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
                .orderId(orderId)
                .trackingCode(response.getTrackingCode())
                .carrierType(carrierType)
                .service(selected != null && selected.getService() != null ? selected.getService() : requestedService)
                .freightChargeAmt(freightChargeAmtCents)
                .shipToAddress(shipToAddress)
                .trackingUrl(trackingUrl)
                .labelFileUrl(labelUrl)
                .issuedAt(java.time.LocalDateTime.now().toString())
                .build();
    }

    /**
     * 유효한 rate 문자열만 대상으로 최저 운임을 선택한다.
     *
     * @param rates EasyPost rate 목록
     * @return 가장 저렴한 rate
     * @throws BusinessException 유효한 rate가 없는 경우 (INT-005)
     */
    public static EasyPostShipmentResponse.RateDto selectCheapestRate(List<EasyPostShipmentResponse.RateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_SHIPPING_RATES);
        }
        return rates.stream()
                .filter(r -> r.getRate() != null && isNumeric(r.getRate()))
                .min(Comparator.comparingDouble(r -> Double.parseDouble(r.getRate())))
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_SHIPPING_RATES));
    }

    // tracker 공개 URL이 있으면 우선 사용하고, 없으면 trackingCode 기반 URL을 만든다.
    private static String resolveTrackingUrl(EasyPostShipmentResponse response) {
        if (response.getTracker() != null && response.getTracker().getPublicUrl() != null) {
            return response.getTracker().getPublicUrl();
        }
        if (response.getTrackingCode() != null) {
            return "https://track.easypost.com/" + response.getTrackingCode();
        }
        return null;
    }

    // 주소 조각을 사람이 읽을 수 있는 한 줄 문자열로 합친다.
    private static String resolveShipToAddress(EasyPostShipmentResponse.AddressDto addr) {
        if (addr == null) return null;
        return Stream.of(addr.getStreet1(), addr.getCity(), addr.getState(), addr.getZip(), addr.getCountry())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    // 운임 문자열이 숫자로 파싱 가능한지 확인한다.
    static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
