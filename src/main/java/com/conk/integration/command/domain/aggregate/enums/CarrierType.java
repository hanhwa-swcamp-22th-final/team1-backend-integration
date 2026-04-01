package com.conk.integration.command.domain.aggregate.enums;

// 현재 시스템이 지원하는 배송사 종류다.
public enum CarrierType {
    USPS("USPS"),
    UPS("UPS"),
    FEDEX("FedEx");  // Shopify 표기는 FedEx

    private final String shopifyName;

    CarrierType(String shopifyName) {
        this.shopifyName = shopifyName;
    }

    // EasyPost carrier 문자열 → enum (null 또는 미지 → USPS)
    public static CarrierType fromEasyPostName(String name) {
        if (name == null) return USPS;
        return switch (name.toUpperCase()) {
            case "UPS"   -> UPS;
            case "FEDEX" -> FEDEX;
            default      -> USPS;
        };
    }

    // Shopify fulfillment API가 기대하는 운송사 표기명을 반환한다.
    public String toShopifyName() {
        return shopifyName;
    }
}
