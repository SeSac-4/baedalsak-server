package com.baedalsak.delivery.location;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 가상 라이더 위치 (delivery-gps.mode=virtual). 처음 보는 라이더는 기준점(가게) 주변 무작위 지점에 두고,
 * 배달을 마치면 배달지에 남긴다. 위치는 Redis 에 있어 파드가 여러 개여도 같은 값을 본다.
 */
@Slf4j
public class VirtualRiderPositionProvider implements RiderPositionProvider {

    /** 위도 1도의 거리 (m) */
    private static final double METERS_PER_DEGREE = 111_320;

    private final RiderLocationStore riderLocationStore;
    private final RiderPositionProperties properties;
    private final RandomGenerator random;

    public VirtualRiderPositionProvider(RiderLocationStore riderLocationStore, RiderPositionProperties properties,
                                        RandomGenerator random) {
        this.riderLocationStore = riderLocationStore;
        this.properties = properties;
        this.random = random;
    }

    /**
     * 저장된 위치가 있으면 그대로, 없으면 기준점 주변 무작위 지점을 저장해 돌려준다.
     * 동시에 두 요청이 와도 먼저 저장된 값 하나만 남는다 (SET NX).
     */
    @Override
    public Optional<GeoPoint> currentPosition(Long riderId, GeoPoint near) {
        Optional<GeoPoint> saved = riderLocationStore.findRiderPosition(riderId);
        if (saved.isPresent() || near == null) {
            return saved;
        }
        GeoPoint start = randomPointAround(near);
        log.info("virtual rider position created: riderId={}", riderId);
        return Optional.of(riderLocationStore.saveRiderPositionIfAbsent(riderId, start).orElse(start));
    }

    @Override
    public void settle(Long riderId, GeoPoint point) {
        riderLocationStore.saveRiderPosition(riderId, point);
    }

    // 기준점에서 반경 [min, max] m 안, 무작위 방향의 지점
    GeoPoint randomPointAround(GeoPoint center) {
        double distanceM = properties.startMinRadiusM()
                + random.nextDouble() * (properties.startMaxRadiusM() - properties.startMinRadiusM());
        double bearing = random.nextDouble() * 2 * Math.PI;
        double dLat = distanceM * Math.cos(bearing) / METERS_PER_DEGREE;
        double dLng = distanceM * Math.sin(bearing) / (METERS_PER_DEGREE * Math.cos(Math.toRadians(center.lat())));
        return new GeoPoint(center.lat() + dLat, center.lng() + dLng);
    }
}
