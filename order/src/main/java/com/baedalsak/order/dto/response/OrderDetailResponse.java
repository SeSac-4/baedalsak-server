package com.baedalsak.order.dto.response;

import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderItem;
import com.baedalsak.order.domain.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주문 상세 (OrderDetail). 주문 생성 응답과 주문 상세 조회가 함께 쓴다 (화면 U-09·O-03).
 * 고객 연락처는 내려주지 않는다 (O-03 '고객 연락처 없음'). 배정·픽업·완료 시각은 delivery API 로 합성한다.
 */
public record OrderDetailResponse(
        Long id,
        Long storeId,
        String storeName,
        OrderStatus status,
        List<OrderItemResponse> items,
        Integer itemsAmount,
        Integer deliveryFee,
        Integer totalAmount,
        String deliveryAddress,
        String storeRequest,
        String riderRequest,
        OffsetDateTime estimatedReadyAt,
        String riderName,
        OffsetDateTime createdAt
) {

    public static OrderDetailResponse of(Order order, List<OrderItem> items) {
        return new OrderDetailResponse(
                order.getId(),
                order.getStoreId(),
                order.getStoreName(),
                order.getStatus(),
                items.stream().map(OrderItemResponse::from).toList(),
                order.getItemsAmount(),
                order.getDeliveryFee(),
                order.getTotalAmount(),
                order.getDeliveryAddress(),
                order.getStoreRequest(),
                order.getRiderRequest(),
                order.getEstimatedReadyAt(),
                order.getRiderName(),
                order.getCreatedAt()
        );
    }
}
