package com.baedalsak.delivery.payload;

import com.baedalsak.delivery.domain.Delivery;

import java.time.OffsetDateTime;

/**
 * 발행: DeliveryPickedUp (delivery.picked_up). 라이더 픽업 완료 시 (설계서 '이벤트 목록' 탭, 6.3.1).
 * 구독: order (orders.status=배달중), delivery 메일 큐 (배달시작 메일).
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
    public static final String ROUTING_KEY = "delivery.picked_up";

    public static DeliveryPickedUpEvent from(Delivery delivery) {
        return new DeliveryPickedUpEvent(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCustomerId(),
                delivery.getRiderId(),
                delivery.getPickedUpAt(),
                delivery.getCustomerEmail());
    }
}
