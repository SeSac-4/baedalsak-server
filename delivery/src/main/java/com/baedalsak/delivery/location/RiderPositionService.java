package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 라이더 현재 위치 (배달과 무관하게 라이더별). 배차 전 지도와 가게까지 경로의 출발점에 쓴다.
 * virtual 모드: 처음 보는 라이더는 기준 가게 바로 주변(50~300m)에 둔다. real 모드: 저장된 값만 읽는다.
 * 위치는 캐시 Redis 에만 있다 — 지워지면 다음에 볼 때 다시 가게 주변에 둔다.
 */
@Slf4j
@Service
public class RiderPositionService {

    /** 가게에서 최소 거리 (m). 가게 핀과 겹치지 않을 만큼만 띄운다 */
    static final double START_MIN_RADIUS_M = 50;
    /** 가게에서 최대 거리 (m). 가게 바로 주변 */
    static final double START_MAX_RADIUS_M = 300;
    private static final double METERS_PER_DEGREE_LAT = 111_320;

    private final RiderLocationStore riderLocationStore;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryGpsProperties properties;
    private final RandomGenerator random;

    @Autowired
    public RiderPositionService(RiderLocationStore riderLocationStore, DeliveryRepository deliveryRepository,
                                DeliveryGpsProperties properties) {
        this(riderLocationStore, deliveryRepository, properties, RandomGenerator.getDefault());
    }

    RiderPositionService(RiderLocationStore riderLocationStore, DeliveryRepository deliveryRepository,
                         DeliveryGpsProperties properties, RandomGenerator random) {
        this.riderLocationStore = riderLocationStore;
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
        this.random = random;
    }

    /**
     * 내 위치 (GET /v1/deliveries/me/position). 아직 없으면 가장 오래 기다린 배차 대기 가게 주변에 둔다.
     *
     * @param riderId 라이더 ID
     * @return 현재 위치. 둘 곳(좌표 있는 배차 대기)이 없거나 real 모드에서 위치를 모르면 빈 값
     */
    public Optional<GeoPoint> myPosition(Long riderId) {
        Optional<GeoPoint> stored = riderLocationStore.findRiderPosition(riderId);
        if (stored.isPresent() || !properties.isVirtual()) {
            return stored;
        }
        return deliveryRepository.findFirstByStatusAndPickupLatIsNotNullOrderByCreatedAtAscIdAsc(DeliveryStatus.WAITING)
                .map(Delivery::getPickupPoint)
                .flatMap(store -> place(riderId, store));
    }

    /**
     * 가게 기준 현재 위치 (배차 수락 때 가게까지 경로의 출발점). 아직 없으면 그 가게 주변에 둔다.
     *
     * @param riderId 라이더 ID
     * @param store   가게 좌표
     * @return 현재 위치. real 모드에서 위치를 모르거나 Redis 실패면 빈 값
     */
    public Optional<GeoPoint> positionNear(Long riderId, GeoPoint store) {
        Optional<GeoPoint> stored = riderLocationStore.findRiderPosition(riderId);
        if (stored.isPresent() || !properties.isVirtual()) {
            return stored;
        }
        return place(riderId, store);
    }

    /**
     * 라이더 위치를 옮긴다 (이동 중 · 배달 완료 후 배달지).
     *
     * @param riderId 라이더 ID
     * @param point   위치
     */
    public void moveTo(Long riderId, GeoPoint point) {
        riderLocationStore.saveRiderPosition(riderId, point);
    }

    // 가게에서 무작위 방향으로 50~300m 떨어진 곳. 동시에 불리면 먼저 저장된 값을 쓴다
    private Optional<GeoPoint> place(Long riderId, GeoPoint store) {
        double distanceM = START_MIN_RADIUS_M + random.nextDouble() * (START_MAX_RADIUS_M - START_MIN_RADIUS_M);
        double bearing = random.nextDouble() * 2 * Math.PI;
        double lat = store.lat() + distanceM * Math.cos(bearing) / METERS_PER_DEGREE_LAT;
        double lng = store.lng() + distanceM * Math.sin(bearing)
                / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(store.lat())));
        Optional<GeoPoint> placed = riderLocationStore.placeRiderPositionIfAbsent(riderId, new GeoPoint(lat, lng));
        placed.ifPresent(point -> log.info("rider position placed near store: riderId={}", riderId));
        return placed;
    }
}
