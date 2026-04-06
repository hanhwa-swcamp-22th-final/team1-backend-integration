package com.conk.integration.command.application.dto.response;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import com.conk.integration.command.domain.aggregate.EasypostShipmentInvoice;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 수동 주문 기입 및 EasyPost 송장 발급 결과를 클라이언트에 노출하는 응답 DTO다.
@Getter
@AllArgsConstructor
public class ManualOrderInvoiceResponse {

    private String orderId;
    private String receiverName;
    private String shipToAddress;
    private List<OrderItemBody> items;
    private String invoiceNo;
    private String trackingCode;
    private String carrierType;
    private int freightChargeAmt;
    private String trackingUrl;
    private String labelFileUrl;

    @Getter
    @AllArgsConstructor
    public static class OrderItemBody {
        private String skuId;
        private String productNameSnapshot;
        private int quantity;
    }

    public static ManualOrderInvoiceResponse of(ChannelOrder order, EasypostShipmentInvoice invoice) {
        List<OrderItemBody> itemBodies = order.getItems().stream()
                .map(item -> new OrderItemBody(
                        item.getId().getSkuId(),
                        item.getProductNameSnapshot(),
                        item.getQuantity()))
                .collect(Collectors.toList());

        String shipToAddress = Stream.of(
                        order.getShipToAddress1(),
                        order.getShipToCity(),
                        order.getShipToState(),
                        order.getShipToZipCode())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));

        return new ManualOrderInvoiceResponse(
                order.getOrderId(),
                order.getReceiverName(),
                shipToAddress,
                itemBodies,
                invoice.getInvoiceNo(),
                invoice.getTrackingCode(),
                invoice.getCarrierType().name(),
                invoice.getFreightChargeAmt(),
                invoice.getTrackingUrl(),
                invoice.getLabelFileUrl()
        );
    }
}
