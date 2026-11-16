package com.baedalsak.delivery.location;

import java.util.Optional;

/**
 * 라이더 현재 위치 출처. 배달에 묶이지 않은 라이더 자체의 위치라 수락 전 대기 목록 · 수락 직후 가게까지 경로에 쓴다.
 * delivery-gps.mode 로 구현을 고른다 (LocationConfig) — virtual 은 서버가 정한 가상 위치, real 은 라이더 앱 GPS.
 * 수락 · 경로 · 조회 코드는 이 인터페이스만 보므로 실제 GPS 로 바꿔도 손대지 않는다.
 */
public interface RiderPositionProvider {

    /**
     * @param riderId 라이더 ID
     * @param near    아직 위치가 없을 때 가상 위치를 잡을 기준점 (가게 좌표 등). 실제 GPS 는 쓰지 않는다. null 가능
     * @return 현재 위치. 알 수 없으면 빈 값
     */
    Optional<GeoPoint> currentPosition(Long riderId, GeoPoint near);

    /**
     * 배달을 마친 자리에 라이더를 둔다. 다음 배차는 이 자리에서 출발한다.
     *
     * @param riderId 라이더 ID
     * @param point   배달지 좌표
     */
    void settle(Long riderId, GeoPoint point);
}
