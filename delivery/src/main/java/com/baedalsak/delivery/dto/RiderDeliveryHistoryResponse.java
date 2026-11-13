package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;

import java.time.OffsetDateTime;

/**
 * 라이더 배달 내역 한 건. 완료한 배달만 담는다.
 * distanceM 은 픽업 때 저장한 경로 거리(m). 경로를 못 구했으면 null.
 */
public record RiderDeliveryHistoryResponse(
        Long deliveryId,
        Long orderId,
        String storeName,
        String deliveryAddress,
        Integer deliveryFee,
        Integer distanceM,
        OffsetDateTime assignedAt,
        OffsetDateTime completedAt
) {
    public static RiderDeliveryHistoryResponse from(Delivery delivery) {
        return new RiderDeliveryHistoryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStoreName(),
                delivery.getDeliveryAddress(),
                delivery.getDeliveryFee(),
                delivery.getRouteDistanceM(),
                delivery.getAssignedAt(),
                delivery.getCompletedAt());
    }
}
