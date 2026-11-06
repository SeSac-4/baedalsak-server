package com.baedalsak.delivery.location;

import java.util.List;

/**
 * 가게 → 배달지 경로.
 *
 * @param path      경로 좌표 (가게에서 배달지 순서, 2개 이상)
 * @param distanceM 전체 거리 (m)
 * @param durationS 예상 소요 시간 (초)
 * @param straight  길찾기 실패로 두 점을 직선으로 이었으면 true
 */
public record Route(List<GeoPoint> path, int distanceM, int durationS, boolean straight) {

    public Route {
        if (path == null || path.size() < 2) {
            throw new IllegalArgumentException("route path needs at least 2 points");
        }
        path = List.copyOf(path);
    }

    /**
     * 길찾기 실패 시 대체 경로. 두 점을 직선으로 잇고, 소요 시간은 속도로 계산한다.
     *
     * @param from     출발 (가게)
     * @param to       도착 (배달지)
     * @param speedKmh 이동 속도 (km/h)
     * @return 직선 경로
     */
    public static Route straight(GeoPoint from, GeoPoint to, double speedKmh) {
        double distance = RouteGeometry.distanceM(from, to);
        int duration = (int) Math.round(distance / (speedKmh * 1000 / 3600));
        return new Route(List.of(from, to), (int) Math.round(distance), duration, true);
    }
}
