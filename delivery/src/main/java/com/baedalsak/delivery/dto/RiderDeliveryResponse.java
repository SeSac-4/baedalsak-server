package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * 배정된 라이더에게 보여 주는 배달 (설계서 'API 명세' RiderDelivery, 화면 R-02).
 * 배차 후라 대기 목록(WaitingDeliveryResponse)과 달리 상세 배달 주소·요청사항을 포함한다.
 * 고객 연락처는 진행 중 배달 조회(6.4.2, from)에만 싣고, 배차 수락·픽업 응답(withoutCustomerPhone)에서는 필드째 뺀다 (N4.4).
 */
public record RiderDeliveryResponse(
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        String storeName,
        String pickupAddress,
        String deliveryAddress,
        @JsonInclude(JsonInclude.Include.NON_NULL) String customerPhone,
        String riderRequest,
        Integer deliveryFee,
        OffsetDateTime estimatedReadyAt,
        OffsetDateTime assignedAt,
        OffsetDateTime pickedUpAt
) {
    // 6.4.2 진행 중 배달 조회. 고객 연락처 포함
    public static RiderDeliveryResponse from(Delivery delivery) {
        return of(delivery, delivery.getCustomerPhone());
    }

    // 6.2.2 배차 수락 · 6.3.1 픽업 완료. 연락처는 R-02 진입 시 GET /me/active 로만 받는다
    public static RiderDeliveryResponse withoutCustomerPhone(Delivery delivery) {
        return of(delivery, null);
    }

    private static RiderDeliveryResponse of(Delivery delivery, String customerPhone) {
        return new RiderDeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getStoreName(),
                delivery.getPickupAddress(),
                delivery.getDeliveryAddress(),
                customerPhone,
                delivery.getRiderRequest(),
                delivery.getDeliveryFee(),
                delivery.getEstimatedReadyAt(),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt());
    }
}
