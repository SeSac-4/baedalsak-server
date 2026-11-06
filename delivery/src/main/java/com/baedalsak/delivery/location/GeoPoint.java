package com.baedalsak.delivery.location;

/**
 * 위경도 좌표 (WGS84). API 응답에서는 {"lat": 37.54, "lng": 127.05} 로 나간다 (프론트 카카오맵 좌표 형식과 같음).
 *
 * @param lat 위도
 * @param lng 경도
 */
public record GeoPoint(double lat, double lng) {

    /**
     * DB 컬럼 두 개를 좌표로 묶는다.
     *
     * @param lat 위도 컬럼 값
     * @param lng 경도 컬럼 값
     * @return 둘 다 있으면 좌표, 하나라도 비면 null
     */
    public static GeoPoint ofNullable(Double lat, Double lng) {
        return lat == null || lng == null ? null : new GeoPoint(lat, lng);
    }
}
