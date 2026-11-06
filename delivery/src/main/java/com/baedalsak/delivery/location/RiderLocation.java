package com.baedalsak.delivery.location;

import java.time.OffsetDateTime;

/**
 * 라이더 현재 위치 (캐시 Redis delivery:location:{deliveryId} 값, 위치 조회 API 응답의 location).
 * 도착(arrived)은 DB 상태가 아니라 이 값에만 있다. 배달 완료는 라이더가 완료 버튼을 눌러야 된다.
 *
 * @param lat                위도
 * @param lng                경도
 * @param remainingDistanceM 경로를 따라 배달지까지 남은 거리 (m). 경로를 모르면 null
 * @param etaAt              도착 예정 시각. 경로를 모르면 null
 * @param arrived            배달지 도착 여부 (한 번 도착하면 계속 true)
 * @param arrivedAt          처음 도착한 시각. 도착 전이면 null
 * @param updatedAt          이 위치를 받은 시각
 * @param source             위치 출처 (가상 / 실제 GPS)
 */
public record RiderLocation(
        double lat,
        double lng,
        Integer remainingDistanceM,
        OffsetDateTime etaAt,
        boolean arrived,
        OffsetDateTime arrivedAt,
        OffsetDateTime updatedAt,
        LocationSource source
) {

    /**
     * @return 좌표만
     */
    public GeoPoint point() {
        return new GeoPoint(lat, lng);
    }
}
