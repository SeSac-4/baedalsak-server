package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.location.GeoPoint;
import com.baedalsak.delivery.location.Route;

import java.util.List;

/**
 * GET /v1/deliveries/by-order/{orderId}/route 응답. 지도 경로선용이라 화면 진입 시 한 번만 부른다.
 *
 * @param deliveryId 배달 ID
 * @param path       경로 좌표 [{lat, lng}, ...] (가게 → 배달지)
 * @param distanceM  전체 거리 (m)
 * @param durationS  예상 소요 시간 (초)
 * @param straight   길찾기 실패로 직선이면 true (점선 등으로 다르게 그릴 수 있음)
 */
public record DeliveryRouteResponse(
        Long deliveryId,
        List<GeoPoint> path,
        int distanceM,
        int durationS,
        boolean straight
) {
    public static DeliveryRouteResponse of(Long deliveryId, Route route) {
        return new DeliveryRouteResponse(deliveryId, route.path(), route.distanceM(), route.durationS(),
                route.straight());
    }
}
