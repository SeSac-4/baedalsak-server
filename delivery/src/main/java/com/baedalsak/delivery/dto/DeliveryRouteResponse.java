package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.location.GeoPoint;
import com.baedalsak.delivery.location.Route;
import com.baedalsak.delivery.location.RouteLeg;

import java.util.List;

/**
 * GET /v1/deliveries/by-order/{orderId}/route 응답. 지도 경로선용이라 구간마다 한 번만 부른다.
 *
 * @param deliveryId 배달 ID
 * @param leg        구간. TO_STORE = 라이더 → 가게 (배차 완료), TO_CUSTOMER = 가게 → 배달지 (픽업 후)
 * @param path       경로 좌표 [{lat, lng}, ...] (구간 출발 → 도착)
 * @param distanceM  전체 거리 (m)
 * @param durationS  예상 소요 시간 (초)
 * @param straight   길찾기 실패로 직선이면 true (점선 등으로 다르게 그릴 수 있음)
 */
public record DeliveryRouteResponse(
        Long deliveryId,
        RouteLeg leg,
        List<GeoPoint> path,
        int distanceM,
        int durationS,
        boolean straight
) {
    public static DeliveryRouteResponse of(Long deliveryId, RouteLeg leg, Route route) {
        return new DeliveryRouteResponse(deliveryId, leg, route.path(), route.distanceM(), route.durationS(),
                route.straight());
    }
}
