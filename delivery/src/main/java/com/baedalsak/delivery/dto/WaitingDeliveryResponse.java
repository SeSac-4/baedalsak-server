package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.location.DeliveryTimeEstimator;
import com.baedalsak.delivery.location.GeoPoint;

import java.time.OffsetDateTime;

/**
 * GET /v1/deliveries/waiting 응답 항목 (6.2.1, 설계서 'API 명세' WaitingDelivery).
 * 배차 전이라 배달지는 동 단위만 보여 주고, 상세 주소·고객 연락처는 넣지 않는다 (N4.4).
 * 같은 이유로 좌표도 픽업(가게) 좌표 pickupPoint 만 싣는다. 주소 변환 전이거나 실패하면 null.
 * 예상 시간(초)은 직선 거리 기준 어림값이다. 모르면 null.
 *
 * @param pickupDurationS   라이더 현재 위치 → 가게
 * @param deliveryDurationS 가게 → 배달지
 * @param totalDurationS    max(조리 완료까지, 가게까지) + 배달 = 배달 완료까지
 */
public record WaitingDeliveryResponse(
        Long deliveryId,
        String storeName,
        String pickupAddress,
        String deliveryDong,
        Integer deliveryFee,
        OffsetDateTime estimatedReadyAt,
        OffsetDateTime createdAt,
        GeoPoint pickupPoint,
        Integer pickupDurationS,
        Integer deliveryDurationS,
        Integer totalDurationS
) {
    public static WaitingDeliveryResponse of(Delivery delivery, DeliveryTimeEstimator.Estimate estimate) {
        return new WaitingDeliveryResponse(
                delivery.getId(),
                delivery.getStoreName(),
                delivery.getPickupAddress(),
                delivery.getDeliveryDong(),
                delivery.getDeliveryFee(),
                delivery.getEstimatedReadyAt(),
                delivery.getCreatedAt(),
                delivery.getPickupPoint(),
                estimate.pickupDurationS(),
                estimate.deliveryDurationS(),
                estimate.totalDurationS());
    }
}
