package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.location.RiderLocation;

/**
 * GET /v1/deliveries/by-order/{orderId}/location 응답 (폴링용, 갱신 주기 2초).
 * 배달 상태를 같이 실어 폴링 한 번으로 픽업 · 완료 같은 상태 변경까지 반영한다.
 *
 * @param deliveryId 배달 ID
 * @param orderId    주문 ID
 * @param status     배달 상태
 * @param location   라이더 위치. 픽업 전 · 완료 후 · 아직 첫 위치 전이면 null
 */
public record DeliveryLocationResponse(
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        RiderLocation location
) {
    public static DeliveryLocationResponse of(Delivery delivery, RiderLocation location) {
        return new DeliveryLocationResponse(delivery.getId(), delivery.getOrderId(), delivery.getStatus(), location);
    }
}
