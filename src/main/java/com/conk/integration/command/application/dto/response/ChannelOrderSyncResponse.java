package com.conk.integration.command.application.dto.response;

import com.conk.integration.command.domain.aggregate.ChannelOrder;
import com.conk.integration.command.domain.aggregate.ChannelOrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// 채널 주문 동기화 결과를 클라이언트에 노출하는 응답 DTO다.
@Getter
@AllArgsConstructor
public class ChannelOrderSyncResponse {

    private int savedCount;
    private int skippedCount;
    private List<OrderDto> orders;

    @Getter
    @AllArgsConstructor
    public static class OrderDto {

        private String orderId;
        private String channelOrderNo;
        private LocalDateTime orderedAt;
        private String receiverName;
        private List<ItemDto> items;

        public static OrderDto from(ChannelOrder order) {
            List<ItemDto> items = order.getItems().stream()
                    .map(ItemDto::from)
                    .toList();
            return new OrderDto(
                    order.getOrderId(),
                    order.getChannelOrderNo(),
                    order.getOrderedAt(),
                    order.getReceiverName(),
                    items
            );
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ItemDto {

        private String skuId;
        private String productName;
        private int quantity;

        public static ItemDto from(ChannelOrderItem item) {
            return new ItemDto(
                    item.getId().getSkuId(),
                    item.getProductNameSnapshot(),
                    item.getQuantity()
            );
        }
    }
}
