package com.baedalsak.delivery.payload;

import com.baedalsak.delivery.domain.Delivery;

import java.time.OffsetDateTime;

/**
 * 발행: DeliveryAssigned (delivery.assigned). 라이더 배차 수락 성공 시 (설계서 '이벤트 목록' 탭, 6.2.2).
 * 구독: order (orders.rider_name 저장), delivery 메일 큐 (배차완료 메일).
 */
public record DeliveryAssignedEvent(
        Long deliveryId,
        Long orderId,
        Long customerId,
        Long ownerId,
        Long riderId,
        String riderName,
        OffsetDateTime assignedAt
) {
    public static final String TYPE = "DeliveryAssigned";
    public static final String ROUTING_KEY = "delivery.assigned";

    public static DeliveryAssignedEvent from(Delivery delivery) {
        return new DeliveryAssignedEvent(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCustomerId(),
                delivery.getOwnerId(),
                delivery.getRiderId(),
                delivery.getRiderName(),
                delivery.getAssignedAt());
    }
}
