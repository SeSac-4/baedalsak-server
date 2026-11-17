package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 배차 수락 직후 라이더 현재 위치 → 가게 경로를 구해 캐시 Redis 에 둔다.
 * 가게까지 경로는 배달이 끝나면 필요 없어 DB 에 남기지 않는다 (마이그레이션 없음).
 * 길찾기가 실패하면 두 점을 직선으로 잇는다. 카카오 호출은 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApproachRouteService {

    private final DeliveryRepository deliveryRepository;
    private final RiderPositionService riderPositionService;
    private final KakaoDirectionsClient kakaoDirectionsClient;
    private final RiderLocationStore riderLocationStore;
    private final DeliveryGpsProperties gpsProperties;

    /**
     * 가게까지 경로가 없으면 구해서 저장한다. 실패해도 예외를 던지지 않는다 (수락은 이미 커밋됨).
     *
     * @param deliveryId 배달 ID
     */
    public void saveRoute(Long deliveryId) {
        try {
            Optional<Delivery> found = deliveryRepository.findById(deliveryId);
            if (found.isEmpty() || found.get().getStatus() != DeliveryStatus.ASSIGNED
                    || riderLocationStore.findApproachRoute(deliveryId).isPresent()) {
                return;
            }
            Delivery delivery = found.get();
            GeoPoint store = delivery.getPickupPoint();
            if (store == null) {
                log.warn("approach route skipped, store coordinates missing: deliveryId={}", deliveryId);
                return;
            }
            Optional<GeoPoint> rider = riderPositionService.positionNear(delivery.getRiderId(), store);
            if (rider.isEmpty()) {
                log.warn("approach route skipped, rider position unknown: deliveryId={}", deliveryId);
                return;
            }

            Route route = kakaoDirectionsClient.findRoute(rider.get(), store)
                    .orElseGet(() -> Route.straight(rider.get(), store,
                            gpsProperties.speedKmhOf(delivery.getVehicleType())));
            riderLocationStore.saveApproachRoute(deliveryId, route);
            log.info("approach route saved: deliveryId={}, points={}, distanceM={}, durationS={}, straight={}",
                    deliveryId, route.path().size(), route.distanceM(), route.durationS(), route.straight());
        } catch (RuntimeException e) {
            log.warn("approach route failed: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }

    /**
     * @param delivery 배달
     * @return 라이더 → 가게 경로. 아직 없거나 Redis 실패면 빈 값
     */
    public Optional<Route> readRoute(Delivery delivery) {
        return riderLocationStore.findApproachRoute(delivery.getId());
    }
}
