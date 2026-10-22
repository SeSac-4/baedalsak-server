package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;

import java.time.OffsetDateTime;

/**
 * GET /v1/deliveries/waiting 응답 항목 (6.2.1, 설계서 'API 명세' WaitingDelivery).
 * 배차 전이라 배달지는 동 단위만 보여 주고, 상세 주소·고객 연락처는 넣지 않는다 (N4.4).
 */
public record WaitingDeliveryResponse(
        Long deliveryId,
        String storeName,
        String pickupAddress,
        String deliveryDong,
        Integer deliveryFee,
        OffsetDateTime estimatedReadyAt,
        OffsetDateTime createdAt
) {
    public static WaitingDeliveryResponse from(Delivery delivery) {
        return new WaitingDeliveryResponse(
                delivery.getId(),
                delivery.getStoreName(),
                delivery.getPickupAddress(),
                delivery.getDeliveryDong(),
                delivery.getDeliveryFee(),
                delivery.getEstimatedReadyAt(),
                delivery.getCreatedAt());
    }
}
