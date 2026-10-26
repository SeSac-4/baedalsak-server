package com.baedalsak.order.event;

import java.time.OffsetDateTime;

/**
 * DeliveryAssigned 이벤트 본문 (라우팅 키 delivery.assigned, 설계서 '이벤트 목록' 탭). 라이더 배차 (6.2.2). rider_name 저장
 */
public record DeliveryAssignedEvent(
        Long deliveryId,
        Long orderId,
        Long customerId,
        Long ownerId,
        Long riderId,
        String riderName,
        OffsetDateTime assignedAt,
        String customerEmail,
        String ownerEmail
) {

    public static final String TYPE = "DeliveryAssigned";
}
