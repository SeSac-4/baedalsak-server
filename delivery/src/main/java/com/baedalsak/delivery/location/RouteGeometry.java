package com.baedalsak.delivery.location;

import java.util.List;

/**
 * 경로 계산 (순수 함수). 가상 GPS 위치 계산과 남은 거리 계산에 쓴다.
 * 배달 거리(수 km)에서는 지구 곡률 오차가 작아, 구간 안 계산은 평면(등장방형) 근사를 쓴다.
 */
public final class RouteGeometry {

    private static final double EARTH_RADIUS_M = 6_371_000;

    private RouteGeometry() {
    }

    /**
     * 두 좌표 사이 거리 (하버사인).
     *
     * @param a 좌표 1
     * @param b 좌표 2
     * @return 거리 (m)
     */
    public static double distanceM(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /**
     * 경로 전체 길이.
     *
     * @param path 경로 좌표
     * @return 길이 (m)
     */
    public static double lengthM(List<GeoPoint> path) {
        double total = 0;
        for (int i = 1; i < path.size(); i++) {
            total += distanceM(path.get(i - 1), path.get(i));
        }
        return total;
    }

    /**
     * 출발점에서 경로를 따라 travelledM 만큼 간 위치. 가상 GPS 가 경과 시간 × 속도로 부른다.
     *
     * @param path       경로 좌표
     * @param travelledM 이동한 거리 (m). 0 이하면 출발점, 경로 길이 이상이면 도착점
     * @return 경로 위 좌표
     */
    public static GeoPoint pointAlong(List<GeoPoint> path, double travelledM) {
        if (travelledM <= 0) {
            return path.getFirst();
        }
        double remaining = travelledM;
        for (int i = 1; i < path.size(); i++) {
            GeoPoint from = path.get(i - 1);
            GeoPoint to = path.get(i);
            double segment = distanceM(from, to);
            if (remaining <= segment) {
                double ratio = segment == 0 ? 0 : remaining / segment;
                return new GeoPoint(from.lat() + (to.lat() - from.lat()) * ratio,
                        from.lng() + (to.lng() - from.lng()) * ratio);
            }
            remaining -= segment;
        }
        return path.getLast();
    }

    /**
     * 현재 위치에서 경로를 따라 도착점까지 남은 거리. 위치를 경로에서 가장 가까운 지점으로 옮겨 계산한다
     * (실제 GPS 는 도로에서 조금 벗어나 들어오므로).
     *
     * @param path    경로 좌표
     * @param current 현재 위치
     * @return 남은 거리 (m)
     */
    public static double remainingM(List<GeoPoint> path, GeoPoint current) {
        double best = Double.MAX_VALUE;
        double bestAlong = 0;
        double along = 0;
        for (int i = 1; i < path.size(); i++) {
            GeoPoint from = path.get(i - 1);
            GeoPoint to = path.get(i);
            double segment = distanceM(from, to);
            double ratio = projectionRatio(from, to, current);
            GeoPoint projected = new GeoPoint(from.lat() + (to.lat() - from.lat()) * ratio,
                    from.lng() + (to.lng() - from.lng()) * ratio);
            double offset = distanceM(current, projected);
            if (offset < best) {
                best = offset;
                bestAlong = along + segment * ratio;
            }
            along += segment;
        }
        return Math.max(0, along - bestAlong);
    }

    // 구간 from→to 위에 current 를 수선으로 내린 위치 (0 = from, 1 = to). 경도는 위도에 맞춰 줄여 평면으로 본다
    private static double projectionRatio(GeoPoint from, GeoPoint to, GeoPoint current) {
        double scale = Math.cos(Math.toRadians(from.lat()));
        double dx = (to.lng() - from.lng()) * scale;
        double dy = to.lat() - from.lat();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return 0;
        }
        double px = (current.lng() - from.lng()) * scale;
        double py = current.lat() - from.lat();
        return Math.clamp((px * dx + py * dy) / lengthSquared, 0, 1);
    }
}
