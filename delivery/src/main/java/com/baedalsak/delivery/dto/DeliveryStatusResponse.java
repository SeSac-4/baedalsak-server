package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;

import java.time.OffsetDateTime;

/**
 * 배달 상태 (설계서 'API 명세' DeliveryStatus). 배달 완료(6.3.2) 응답, 주문의 배달 상태(6.4.1) 응답.
 * 진행이 끝났거나 고객·사장님이 보는 화면이라 RiderDeliveryResponse 와 달리 고객 연락처(customerPhone)를 싣지 않는다.
 */
public record DeliveryStatusResponse(
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        String storeName,
        Long riderId,
        String riderName,
        OffsetDateTime assignedAt,
        OffsetDateTime pickedUpAt,
        OffsetDateTime completedAt
) {
    public static DeliveryStatusResponse from(Delivery delivery) {
        return new DeliveryStatusResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getStoreName(),
                delivery.getRiderId(),
                delivery.getRiderName(),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt(),
                delivery.getCompletedAt());
    }
}
