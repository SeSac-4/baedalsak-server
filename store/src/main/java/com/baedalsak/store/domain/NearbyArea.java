package com.baedalsak.store.domain;

/**
 * 반경 조회 영역: 고객 위치 + 반경 (#210).
 * StoreRepository 의 반경 조건이 위도 · 경도 범위(bounding box)로 먼저 좁힌 뒤 Haversine 으로 정확히 거른다.
 *
 * @param center  고객 위치
 * @param radiusM 반경 (m)
 */
public record NearbyArea(GeoPoint center, int radiusM) {

    // 위도 1도의 거리 (m)
    private static final double METERS_PER_DEGREE = Math.toRadians(1) * GeoPoint.EARTH_RADIUS_M;

    public double lat() {
        return center.lat();
    }

    public double lng() {
        return center.lng();
    }

    public double minLat() {
        return center.lat() - latDelta();
    }

    public double maxLat() {
        return center.lat() + latDelta();
    }

    public double minLng() {
        return center.lng() - lngDelta();
    }

    public double maxLng() {
        return center.lng() + lngDelta();
    }

    // 고객 위치에서 가게까지 직선거리 (m 정수). 가게 좌표가 없으면 null
    public Integer distanceTo(Store store) {
        GeoPoint location = store.getLocation();
        return location == null ? null : (int) Math.round(center.distanceTo(location));
    }

    private double latDelta() {
        return radiusM / METERS_PER_DEGREE;
    }

    // 경도 1도의 거리는 위도가 높을수록 짧다 (× cos 위도). 극에 가까우면 경도 전체
    // 날짜변경선(±180) 너머는 고려하지 않는다 (서비스 지역이 국내)
    private double lngDelta() {
        double cos = Math.cos(Math.toRadians(center.lat()));
        return cos < 1e-6 ? 360 : radiusM / (METERS_PER_DEGREE * cos);
    }
}
