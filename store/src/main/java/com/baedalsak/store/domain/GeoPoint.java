package com.baedalsak.store.domain;

/**
 * 위도 · 경도 좌표 (#210)
 *
 * @param lat 위도
 * @param lng 경도
 */
public record GeoPoint(double lat, double lng) {

    // 지구 반지름 (m). StoreRepository 의 반경 조건과 같은 값을 쓴다
    public static final double EARTH_RADIUS_M = 6_371_000;

    /**
     * 두 좌표 사이 직선거리 (Haversine)
     *
     * @return 거리 (m)
     */
    public double distanceTo(GeoPoint other) {
        double dLat = Math.toRadians(other.lat - lat);
        double dLng = Math.toRadians(other.lng - lng);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
