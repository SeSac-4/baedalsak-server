package com.baedalsak.delivery.payload;

import com.baedalsak.delivery.domain.Delivery;

import java.time.OffsetDateTime;

/**
 * 발행: DeliveryCompleted (delivery.completed). 라이더 배달 완료 시 (설계서 '이벤트 목록' 탭, 6.3.2).
 * 구독: order (orders.status=배달완료), delivery 메일 큐 (고객·사장님 배달완료 메일).
 */
public record DeliveryCompletedEvent(
        Long deliveryId,
        Long orderId,
        Long customerId,
        Long ownerId,
        Long riderId,
        OffsetDateTime completedAt,
        String customerEmail,
        String ownerEmail
) {
    public static final String TYPE = "DeliveryCompleted";
    public static final String ROUTING_KEY = "delivery.completed";

    public static DeliveryCompletedEvent from(Delivery delivery) {
        return new DeliveryCompletedEvent(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCustomerId(),
                delivery.getOwnerId(),
                delivery.getRiderId(),
                delivery.getCompletedAt(),
                delivery.getCustomerEmail(),
                delivery.getOwnerEmail());
    }
}
