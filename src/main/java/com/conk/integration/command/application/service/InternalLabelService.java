package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.EasyPostCreateShipmentRequest;
import com.conk.integration.command.application.dto.request.InternalLabelRequest;
import com.conk.integration.command.application.dto.response.InternalLabelRecommendationResponse;
import com.conk.integration.command.application.dto.response.InternalLabelResponse;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import com.conk.integration.command.domain.aggregate.enums.CarrierType;
import com.conk.integration.command.infrastructure.repository.EasypostShipmentInvoiceRepository;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostApiClient;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostShipmentConverter;
import com.conk.integration.command.infrastructure.service.easypost.EasyPostShipmentResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WMS 내부 송장 발급/추천/조회 파사드다.
 */
@Service
public class InternalLabelService {

    private static final String DEFAULT_CARRIER = "USPS";
    private static final String DEFAULT_SERVICE = "Ground Advantage";

    private final EasyPostApiClient easyPostApiClient;
    private final EasypostShipmentInvoiceRepository invoiceRepository;

    public InternalLabelService(EasyPostApiClient easyPostApiClient,
                                EasypostShipmentInvoiceRepository invoiceRepository) {
        this.easyPostApiClient = easyPostApiClient;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public InternalLabelResponse issueLabel(InternalLabelRequest request) {
        EasyPostCreateShipmentRequest shipmentRequest = toShipmentRequest(request);
        EasyPostShipmentResponse shipment = easyPostApiClient.createShipment(shipmentRequest);
        EasyPostShipmentResponse.RateDto rate = selectRate(shipment.getRates(), request.getCarrier(), request.getService());
        EasyPostShipmentResponse bought = easyPostApiClient.buyRate(shipment.getId(), rate.getId());
        EasypostShipmentInvoice invoice = EasyPostShipmentConverter.toInvoice(
                bought,
                request.getOrderId(),
                rate.getService()
        );
        EasypostShipmentInvoice saved = invoiceRepository.save(invoice);
        return InternalLabelResponse.from(saved);
    }

    public InternalLabelRecommendationResponse recommendLabel(InternalLabelRequest request) {
        double weightOz = request.getParcel() == null || request.getParcel().getWeight() == null
                ? 0.0
                : request.getParcel().getWeight();
        double weightLbs = round(weightOz / 16.0d);
        String carrier = hasText(request.getCarrier()) ? request.getCarrier() : recommendCarrier(weightOz);
        String service = hasText(request.getService()) ? request.getService() : recommendService(weightOz, carrier);
        double estimatedRate = estimateRate(weightOz, carrier);

        return InternalLabelRecommendationResponse.builder()
                .recommendedCarrier(carrier)
                .recommendedService(service)
                .estimatedRate(estimatedRate)
                .weightLbs(weightLbs)
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, InternalLabelResponse> getLabels(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }

        Map<String, InternalLabelResponse> result = new LinkedHashMap<>();
        invoiceRepository.findAllByOrderIdIn(orderIds).forEach(invoice -> {
            if (invoice.getOrderId() != null) {
                result.put(invoice.getOrderId(), InternalLabelResponse.from(invoice));
            }
        });
        return result;
    }

    private EasyPostCreateShipmentRequest toShipmentRequest(InternalLabelRequest request) {
        return EasyPostCreateShipmentRequest.builder()
                .shipment(EasyPostCreateShipmentRequest.ShipmentBody.builder()
                        .toAddress(toAddressBody(request.getToAddress()))
                        .fromAddress(toAddressBody(request.getFromAddress()))
                        .parcel(toParcelBody(request.getParcel()))
                        .build())
                .build();
    }

    private EasyPostCreateShipmentRequest.AddressBody toAddressBody(InternalLabelRequest.AddressDto address) {
        if (address == null) {
            return null;
        }
        return EasyPostCreateShipmentRequest.AddressBody.builder()
                .name(address.getName())
                .street1(address.getStreet1())
                .street2(address.getStreet2())
                .city(address.getCity())
                .state(address.getState())
                .zip(address.getZip())
                .country(address.getCountry())
                .phone(address.getPhone())
                .email(address.getEmail())
                .build();
    }

    private EasyPostCreateShipmentRequest.ParcelBody toParcelBody(InternalLabelRequest.ParcelDto parcel) {
        if (parcel == null) {
            return null;
        }
        return EasyPostCreateShipmentRequest.ParcelBody.builder()
                .weight(parcel.getWeight())
                .length(parcel.getLength())
                .width(parcel.getWidth())
                .height(parcel.getHeight())
                .build();
    }

    private EasyPostShipmentResponse.RateDto selectRate(List<EasyPostShipmentResponse.RateDto> rates,
                                                        String carrier,
                                                        String service) {
        if (rates == null || rates.isEmpty()) {
            return EasyPostShipmentConverter.selectCheapestRate(rates);
        }

        return rates.stream()
                .filter(rate -> matches(rate.getCarrier(), carrier))
                .filter(rate -> matches(rate.getService(), service))
                .findFirst()
                .orElseGet(() -> EasyPostShipmentConverter.selectCheapestRate(rates));
    }

    private boolean matches(String actual, String expected) {
        if (!hasText(expected)) {
            return true;
        }
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    private String recommendCarrier(double weightOz) {
        return weightOz <= 16.0d ? CarrierType.USPS.name() : CarrierType.UPS.name();
    }

    private String recommendService(double weightOz, String carrier) {
        if ("UPS".equalsIgnoreCase(carrier)) {
            return weightOz > 160.0d ? "Ground" : "Ground Saver";
        }
        if ("FEDEX".equalsIgnoreCase(carrier)) {
            return "Ground Economy";
        }
        return DEFAULT_SERVICE;
    }

    private double estimateRate(double weightOz, String carrier) {
        double base = switch (carrier == null ? DEFAULT_CARRIER : carrier.toUpperCase()) {
            case "UPS" -> 7.25d;
            case "FEDEX" -> 8.10d;
            default -> 4.95d;
        };
        double variable = Math.max(weightOz, 1.0d) * 0.18d;
        return round(base + variable);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
