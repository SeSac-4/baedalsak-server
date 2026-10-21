package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;

import java.time.OffsetDateTime;

/**
 * POST /v1/deliveries 응답 (6.1.1).
 */
public record DeliveryCreateResponse(
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        OffsetDateTime createdAt
) {
    public static DeliveryCreateResponse from(Delivery delivery) {
        return new DeliveryCreateResponse(
                delivery.getId(), delivery.getOrderId(), delivery.getStatus(), delivery.getCreatedAt());
    }
}
