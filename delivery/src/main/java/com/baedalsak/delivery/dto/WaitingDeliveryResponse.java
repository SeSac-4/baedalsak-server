package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.location.GeoPoint;

import java.time.OffsetDateTime;

/**
 * GET /v1/deliveries/waiting 응답 항목 (6.2.1, 설계서 'API 명세' WaitingDelivery).
 * 배차 전이라 배달지는 동 단위만 보여 주고, 상세 주소·고객 연락처는 넣지 않는다 (N4.4).
 * 같은 이유로 좌표도 픽업(가게) 좌표 pickupPoint 만 싣는다. 주소 변환 전이거나 실패하면 null.
 */
public record WaitingDeliveryResponse(
        Long deliveryId,
        String storeName,
        String pickupAddress,
        String deliveryDong,
        Integer deliveryFee,
        OffsetDateTime estimatedReadyAt,
        OffsetDateTime createdAt,
        GeoPoint pickupPoint
) {
    public static WaitingDeliveryResponse from(Delivery delivery) {
        return new WaitingDeliveryResponse(
                delivery.getId(),
                delivery.getStoreName(),
                delivery.getPickupAddress(),
                delivery.getDeliveryDong(),
                delivery.getDeliveryFee(),
                delivery.getEstimatedReadyAt(),
                delivery.getCreatedAt(),
                delivery.getPickupPoint());
    }
}
