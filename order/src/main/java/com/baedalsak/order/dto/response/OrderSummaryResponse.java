package com.baedalsak.order.dto.response;

import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderItem;
import com.baedalsak.order.domain.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내 주문 목록 항목 (GET /v1/orders, 화면 U-08 주문 카드: 가게명·메뉴 요약·금액·상태·시각)
 */
public record OrderSummaryResponse(
        Long id,
        String storeName,
        OrderStatus status,
        List<OrderItemResponse> items,
        Integer totalAmount,
        OffsetDateTime createdAt
) {

    public static OrderSummaryResponse of(Order order, List<OrderItem> items) {
        return new OrderSummaryResponse(order.getId(), order.getStoreName(), order.getStatus(),
                items.stream().map(OrderItemResponse::from).toList(), order.getTotalAmount(), order.getCreatedAt());
    }
}
