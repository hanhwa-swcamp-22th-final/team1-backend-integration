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

    /**
     * EasyPost carrier 명칭을 내부 CarrierType으로 변환한다.
     * 알 수 없는 명칭은 USPS로 폴백한다.
     *
     * @param name EasyPost API에서 반환된 carrier 명칭 (null 허용)
     * @return 매핑된 CarrierType
     */
    public static CarrierType fromEasyPostName(String name) {
        if (name == null) return USPS;
        return switch (name.toUpperCase()) {
            case "UPS"   -> UPS;
            case "FEDEX" -> FEDEX;
            default      -> USPS;
        };
    }

    /**
     * 내부 CarrierType을 Shopify fulfillment API의 trackingCompany 값으로 변환한다.
     *
     * @return Shopify trackingCompany 문자열
     */
    public String toShopifyName() {
        return shopifyName;
    }
}
