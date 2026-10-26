package com.baedalsak.order.event;

import java.time.OffsetDateTime;

/**
 * DeliveryCompleted 이벤트 본문 (라우팅 키 delivery.completed, 설계서 '이벤트 목록' 탭). 배달 완료 (6.3.2). DELIVERING → DELIVERED
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
}
