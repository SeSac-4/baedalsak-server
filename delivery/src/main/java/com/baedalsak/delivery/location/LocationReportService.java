package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 위치 수신 공통 입구 (배달실시간초안 8 · 9번). 가상 GPS 와 실제 GPS 가 같은 계산을 탄다.
 * 경로 기준 남은 거리 · 도착 예정 시각을 계산하고, 배달지 반경 안이면 도착 플래그를 세워 Redis 에 저장한다.
 * 도착해도 배달 완료로 바꾸지 않는다 — 완료는 라이더가 완료 버튼(POST /{id}/complete)을 눌러야 한다.
 */
@Slf4j
@Service
public class LocationReportService {

    // 실제 GPS 가 직전 위치보다 이만큼은 뒤로 튈 수 있다고 본다 (도심 GPS 오차 수준)
    static final double BACKTRACK_TOLERANCE_M = 50;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteService deliveryRouteService;
    private final RiderLocationStore riderLocationStore;
    private final DeliveryGpsProperties properties;
    private final Clock clock;

    public LocationReportService(DeliveryRepository deliveryRepository, DeliveryRouteService deliveryRouteService,
                                 RiderLocationStore riderLocationStore, DeliveryGpsProperties properties,
                                 Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryRouteService = deliveryRouteService;
        this.riderLocationStore = riderLocationStore;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 실제 GPS 위치 업로드 (PUT /v1/deliveries/{id}/location). 배정된 라이더 본인 · 픽업 후(PICKED_UP)만.
     * 실제 위치가 들어오면 그 배달의 가상 GPS 는 멈춘다 (둘이 번갈아 쓰지 않게).
     *
     * @param deliveryId 배달 ID
     * @param riderId    요청한 라이더
     * @param point      현재 좌표
     * @return 저장한 위치
     */
    public RiderLocation reportReal(Long deliveryId, Long riderId, GeoPoint point) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        if (!Objects.equals(delivery.getRiderId(), riderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인에게 배정된 배달만 위치를 보낼 수 있습니다.");
        }
        if (delivery.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATUS);
        }
        riderLocationStore.stopSimulation(deliveryId);
        return report(delivery, routeOf(delivery).orElse(null), point, LocationSource.REAL);
    }

    /**
     * 위치를 계산해 저장한다. 상태 · 권한 확인은 부르는 쪽이 한다.
     *
     * @param delivery 배달 (PICKED_UP)
     * @param route    경로. 모르면 null → 남은 거리 · 도착 예정 시각 없이 좌표만 저장
     * @param point    현재 좌표
     * @param source   위치 출처
     * @return 저장한 위치
     */
    public RiderLocation report(Delivery delivery, Route route, GeoPoint point, LocationSource source) {
        return report(delivery, route, point, source, null);
    }

    /**
     * 위치를 계산해 저장한다. 상태 · 권한 확인은 부르는 쪽이 한다.
     *
     * @param delivery   배달 (PICKED_UP)
     * @param route      경로. 모르면 null → 남은 거리 · 도착 예정 시각 없이 좌표만 저장
     * @param point      현재 좌표
     * @param source     위치 출처
     * @param travelledM 경로를 따라 이동한 거리를 알면 그 값 (가상 GPS). null 이면 좌표로 추정 (실제 GPS)
     * @return 저장한 위치
     */
    public RiderLocation report(Delivery delivery, Route route, GeoPoint point, LocationSource source,
                                Double travelledM) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<RiderLocation> previous = riderLocationStore.find(delivery.getId());

        Integer remaining = route == null ? null
                : (int) Math.round(remainingM(route, point, travelledM, previous.orElse(null)));

        boolean wasArrived = previous.map(RiderLocation::arrived).orElse(false);
        boolean arrived = wasArrived || isWithinArrivalRadius(delivery, remaining, point);
        OffsetDateTime arrivedAt = wasArrived ? previous.get().arrivedAt() : (arrived ? now : null);
        if (arrived && !wasArrived) {
            log.info("rider arrived: deliveryId={}, source={}", delivery.getId(), source);
        }
        // 도착 후에는 도착 예정 시각을 도착 시각으로 고정한다 (매번 "지금"으로 바뀌지 않게)
        OffsetDateTime eta = arrived ? arrivedAt
                : remaining == null ? null : now.plusSeconds(Math.round(remaining / speedMps(route, source)));

        RiderLocation location = new RiderLocation(point.lat(), point.lng(), remaining, eta,
                arrived, arrivedAt, now, source);
        riderLocationStore.save(delivery.getId(), location);
        return location;
    }

    /**
     * 경로를 읽는다. 경로가 아직 없지만 배달지 좌표가 있으면 가게 → 배달지 직선으로 대신한다.
     *
     * @param delivery 배달
     * @return 경로. 배달지 좌표도 없으면 빈 값
     */
    public Optional<Route> routeOf(Delivery delivery) {
        Optional<Route> saved = deliveryRouteService.readRoute(delivery);
        if (saved.isPresent() || delivery.getPickupPoint() == null || delivery.getDeliveryPoint() == null) {
            return saved;
        }
        return Optional.of(Route.straight(delivery.getPickupPoint(), delivery.getDeliveryPoint(), properties.speedKmh()));
    }

    // 가상 GPS: 이동 거리를 알아 정확히 계산. 실제 GPS: 직전 위치에서 GPS 오차만큼 뒤까지만 보고 가장 가까운 지점을 찾는다
    private static double remainingM(Route route, GeoPoint point, Double travelledM, RiderLocation previous) {
        double length = RouteGeometry.lengthM(route.path());
        if (travelledM != null) {
            return Math.max(0, length - travelledM);
        }
        double fromAlong = previous == null || previous.remainingDistanceM() == null ? 0
                : Math.max(0, length - previous.remainingDistanceM() - BACKTRACK_TOLERANCE_M);
        return RouteGeometry.remainingM(route.path(), point, fromAlong);
    }

    // 경로상 남은 거리 또는 배달지까지 직선 거리가 반경 안이면 도착 (실제 GPS 가 도로 끝을 지나치는 경우 대비)
    private boolean isWithinArrivalRadius(Delivery delivery, Integer remaining, GeoPoint point) {
        double radius = properties.arrivalRadiusM();
        if (remaining != null && remaining <= radius) {
            return true;
        }
        GeoPoint destination = delivery.getDeliveryPoint();
        return destination != null && RouteGeometry.distanceM(point, destination) <= radius;
    }

    // 가상 GPS 는 설정 속도로 움직이므로 그 속도로, 실제 GPS 는 길찾기 예상 시간(교통 반영) 비율로 계산한다
    private double speedMps(Route route, LocationSource source) {
        double configured = properties.speedKmh() * 1000 / 3600;
        if (source == LocationSource.VIRTUAL || route.straight() || route.durationS() <= 0 || route.distanceM() <= 0) {
            return configured;
        }
        return (double) route.distanceM() / route.durationS();
    }
}
