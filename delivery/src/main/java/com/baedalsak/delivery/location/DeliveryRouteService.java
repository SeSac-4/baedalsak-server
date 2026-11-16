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
 * 픽업 직후 가게 → 배달지 경로를 구해 저장한다 (배달실시간초안 6번).
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
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    public DeliveryRouteService(DeliveryRepository deliveryRepository, DeliveryGeocodeService deliveryGeocodeService,
                                KakaoDirectionsClient kakaoDirectionsClient, DeliveryGpsProperties gpsProperties,
                                JsonMapper jsonMapper, PlatformTransactionManager transactionManager) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryGeocodeService = deliveryGeocodeService;
        this.kakaoDirectionsClient = kakaoDirectionsClient;
        this.gpsProperties = gpsProperties;
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

            Route route = kakaoDirectionsClient.findRoute(from, to)
                    .orElseGet(() -> Route.straight(from, to, gpsProperties.speedKmhOf(delivery.getVehicleType())));
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
     * 저장된 경로를 읽는다. 가상 GPS · 경로 조회 API 가 쓴다.
     *
     * @param delivery 배달
     * @return 경로. 아직 없거나 읽을 수 없으면 빈 값
     */
    public Optional<Route> readRoute(Delivery delivery) {
        if (delivery.getRoutePath() == null) {
            return Optional.empty();
        }
        try {
            List<GeoPoint> path = jsonMapper.readValue(delivery.getRoutePath(), PATH_TYPE);
            // straight 는 저장하지 않으므로 점 2개(가게 · 배달지)뿐이면 직선으로 본다
            return Optional.of(new Route(path, delivery.getRouteDistanceM(), delivery.getRouteDurationS(),
                    path.size() == 2));
        } catch (JacksonException | IllegalArgumentException e) {
            log.warn("delivery route unreadable: deliveryId={}, cause={}", delivery.getId(), e.toString());
            return Optional.empty();
        }
    }
}
