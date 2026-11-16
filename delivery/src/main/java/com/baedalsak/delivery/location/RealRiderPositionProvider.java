package com.baedalsak.delivery.location;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * 실제 GPS 라이더 위치 (delivery-gps.mode=real). 라이더 앱이 올린 마지막 위치를 읽기만 한다.
 * 위치를 직접 만들거나 옮기지 않는다 — 배달을 마친 뒤 위치도 앱이 계속 올린다.
 */
@RequiredArgsConstructor
public class RealRiderPositionProvider implements RiderPositionProvider {

    private final RiderLocationStore riderLocationStore;

    @Override
    public Optional<GeoPoint> currentPosition(Long riderId, GeoPoint near) {
        return riderLocationStore.findRiderPosition(riderId);
    }

    @Override
    public void settle(Long riderId, GeoPoint point) {
        // 실제 GPS 가 위치의 기준이라 서버가 덮어쓰지 않는다
    }
}
