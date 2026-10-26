package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;

import java.time.OffsetDateTime;

/**
 * 배정된 라이더에게 보여 주는 배달 (설계서 'API 명세' RiderDelivery, 화면 R-02).
 * 배차 후라 대기 목록(WaitingDeliveryResponse)과 달리 상세 배달 주소·고객 연락처·요청사항을 포함한다.
 */
public record RiderDeliveryResponse(
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        String storeName,
        String pickupAddress,
        String deliveryAddress,
        String customerPhone,
        String riderRequest,
        Integer deliveryFee,
        OffsetDateTime estimatedReadyAt,
        OffsetDateTime assignedAt,
        OffsetDateTime pickedUpAt
) {
    public static RiderDeliveryResponse from(Delivery delivery) {
        return new RiderDeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getStoreName(),
                delivery.getPickupAddress(),
                delivery.getDeliveryAddress(),
                delivery.getCustomerPhone(),
                delivery.getRiderRequest(),
                delivery.getDeliveryFee(),
                delivery.getEstimatedReadyAt(),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt());
    }
}
