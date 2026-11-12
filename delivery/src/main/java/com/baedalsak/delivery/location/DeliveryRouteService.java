package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/**
 * 경로를 구해 저장한다. 배차 수락 직후 라이더 현재 위치 → 가게 (#193), 픽업 직후 가게 → 배달지 (배달실시간초안 6번).
 * 길찾기가 실패하면 두 점을 직선으로 잇는다 — 위치 표시 · 가상 GPS 는 경로가 있어야 동작하므로 항상 무언가는 저장한다.
 * 카카오 호출은 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
public class DeliveryRouteService {

    private static final TypeReference<List<GeoPoint>> PATH_TYPE = new TypeReference<>() {
    };

    private final DeliveryRepository deliveryRepository;
    private final DeliveryGeocodeService deliveryGeocodeService;
    private final KakaoDirectionsClient kakaoDirectionsClient;
    private final DeliveryGpsProperties gpsProperties;
    private final RiderPositionProvider riderPositionProvider;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    public DeliveryRouteService(DeliveryRepository deliveryRepository, DeliveryGeocodeService deliveryGeocodeService,
                                KakaoDirectionsClient kakaoDirectionsClient, DeliveryGpsProperties gpsProperties,
                                RiderPositionProvider riderPositionProvider, JsonMapper jsonMapper,
                                PlatformTransactionManager transactionManager) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryGeocodeService = deliveryGeocodeService;
        this.kakaoDirectionsClient = kakaoDirectionsClient;
        this.gpsProperties = gpsProperties;
        this.riderPositionProvider = riderPositionProvider;
        this.jsonMapper = jsonMapper;
        // 픽업 커밋 후(AFTER_COMMIT) 리스너에서 불리므로 항상 새 트랜잭션 (DeliveryGeocodeService 와 같은 이유)
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 경로가 없으면 구해서 저장한다. 좌표가 비어 있으면 먼저 한 번 더 변환을 시도하고, 그래도 없으면 건너뛴다.
     * 실패해도 예외를 던지지 않는다 (픽업은 이미 커밋됨).
     *
     * @param deliveryId 배달 ID
     */
    public void saveRoute(Long deliveryId) {
        try {
            deliveryGeocodeService.fillMissingCoordinates(deliveryId);
            Optional<Delivery> found = deliveryRepository.findById(deliveryId);
            if (found.isEmpty() || found.get().getRoutePath() != null) {
                return;
            }
            Delivery delivery = found.get();
            GeoPoint from = delivery.getPickupPoint();
            GeoPoint to = delivery.getDeliveryPoint();
            if (from == null || to == null) {
                log.warn("route skipped, coordinates missing: deliveryId={}", deliveryId);
                return;
            }

            Route route = findRoute(from, to);
            String path = jsonMapper.writeValueAsString(route.path());
            transactionTemplate.executeWithoutResult(status -> deliveryRepository.saveRouteIfAbsent(
                    deliveryId, path, route.distanceM(), route.durationS()));
            log.info("delivery route saved: deliveryId={}, points={}, distanceM={}, durationS={}, straight={}",
                    deliveryId, route.path().size(), route.distanceM(), route.durationS(), route.straight());
        } catch (RuntimeException e) {
            log.warn("delivery route failed: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }

    /**
     * 라이더 현재 위치 → 가게 경로가 없으면 구해서 저장한다. 출발점은 RiderPositionProvider 가 준다
     * (가상 위치든 실제 GPS 든 같은 흐름). 위치를 모르거나 가게 좌표가 없으면 건너뛴다.
     * 실패해도 예외를 던지지 않는다 (수락은 이미 커밋됨).
     *
     * @param deliveryId 배달 ID
     */
    public void saveApproachRoute(Long deliveryId) {
        try {
            deliveryGeocodeService.fillMissingCoordinates(deliveryId);
            Optional<Delivery> found = deliveryRepository.findById(deliveryId);
            if (found.isEmpty() || found.get().getApproachPath() != null || found.get().getRiderId() == null) {
                return;
            }
            Delivery delivery = found.get();
            GeoPoint store = delivery.getPickupPoint();
            if (store == null) {
                log.warn("approach route skipped, store coordinates missing: deliveryId={}", deliveryId);
                return;
            }
            Optional<GeoPoint> rider = riderPositionProvider.currentPosition(delivery.getRiderId(), store);
            if (rider.isEmpty()) {
                log.warn("approach route skipped, rider position unknown: deliveryId={}, riderId={}",
                        deliveryId, delivery.getRiderId());
                return;
            }

            Route route = findRoute(rider.get(), store);
            String path = jsonMapper.writeValueAsString(route.path());
            transactionTemplate.executeWithoutResult(status -> deliveryRepository.saveApproachRouteIfAbsent(
                    deliveryId, path, route.distanceM(), route.durationS()));
            log.info("approach route saved: deliveryId={}, points={}, distanceM={}, durationS={}, straight={}",
                    deliveryId, route.path().size(), route.distanceM(), route.durationS(), route.straight());
        } catch (RuntimeException e) {
            log.warn("approach route failed: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }

    /**
     * 저장된 가게 → 배달지 경로를 읽는다. 가상 GPS · 경로 조회 API 가 쓴다.
     *
     * @param delivery 배달
     * @return 경로. 아직 없거나 읽을 수 없으면 빈 값
     */
    public Optional<Route> readRoute(Delivery delivery) {
        return parse(delivery.getId(), delivery.getRoutePath(), delivery.getRouteDistanceM(),
                delivery.getRouteDurationS());
    }

    /**
     * 저장된 라이더 → 가게 경로를 읽는다. 경로 조회 API 가 배차 완료(ASSIGNED) 때 쓴다.
     *
     * @param delivery 배달
     * @return 경로. 아직 없거나 읽을 수 없으면 빈 값
     */
    public Optional<Route> readApproachRoute(Delivery delivery) {
        return parse(delivery.getId(), delivery.getApproachPath(), delivery.getApproachDistanceM(),
                delivery.getApproachDurationS());
    }

    // 길찾기 실패 시 두 점 직선
    private Route findRoute(GeoPoint from, GeoPoint to) {
        return kakaoDirectionsClient.findRoute(from, to)
                .orElseGet(() -> Route.straight(from, to, gpsProperties.speedKmh()));
    }

    private Optional<Route> parse(Long deliveryId, String json, Integer distanceM, Integer durationS) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            List<GeoPoint> path = jsonMapper.readValue(json, PATH_TYPE);
            // straight 는 저장하지 않으므로 점 2개(출발 · 도착)뿐이면 직선으로 본다
            return Optional.of(new Route(path, distanceM, durationS, path.size() == 2));
        } catch (JacksonException | IllegalArgumentException e) {
            log.warn("delivery route unreadable: deliveryId={}, cause={}", deliveryId, e.toString());
            return Optional.empty();
        }
    }
}
