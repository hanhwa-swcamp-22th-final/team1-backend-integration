package com.conk.integration.command.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 수동 주문 기입 및 EasyPost 송장 발급 단일 요청 DTO다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ManualOrderInvoiceRequest {

    private String orderId;

    private String receiverName;

    private String receiverPhoneNo;

    private String shipToAddress1;

    private String shipToAddress2;

    private String shipToState;

    private String shipToCity;

    private String shipToZipCode;

    private List<OrderItemBody> items;

    private EasyPostCreateShipmentRequest.AddressBody fromAddress;

    private EasyPostCreateShipmentRequest.ParcelBody parcel;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemBody {
        private String skuId;
        private String productNameSnapshot;
        private int quantity;
    }
}
