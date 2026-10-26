package com.baedalsak.order.event;

import java.time.OffsetDateTime;

/**
 * DeliveryPickedUp 이벤트 본문 (라우팅 키 delivery.picked_up, 설계서 '이벤트 목록' 탭). 픽업 완료 (6.3.1). COOKING → DELIVERING
 */
public record DeliveryPickedUpEvent(
        Long deliveryId,
        Long orderId,
        Long customerId,
        Long riderId,
        OffsetDateTime pickedUpAt,
        String customerEmail
) {

    public static final String TYPE = "DeliveryPickedUp";
}
