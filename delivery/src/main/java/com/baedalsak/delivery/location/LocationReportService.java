package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.domain.VehicleType;
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
 * 지금 구간(배차 완료 = 가게까지, 픽업 후 = 배달지까지) 경로 기준 남은 거리 · 도착 예정 시각을 계산하고,
 * 구간 끝 반경 안이면 도착 플래그를 세워 Redis 에 저장한다.
 * 도착해도 상태를 바꾸지 않는다 — 픽업 · 완료는 라이더가 버튼(POST /{id}/pickup · complete)을 눌러야 한다.
 */
@Slf4j
@Service
public class LocationReportService {

    // 실제 GPS 가 직전 위치보다 이만큼은 뒤로 튈 수 있다고 본다 (도심 GPS 오차 수준)
    static final double BACKTRACK_TOLERANCE_M = 50;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteService deliveryRouteService;
    private final ApproachRouteService approachRouteService;
    private final RiderLocationStore riderLocationStore;
    private final RiderPositionService riderPositionService;
    private final DeliveryGpsProperties properties;
    private final Clock clock;

    public LocationReportService(DeliveryRepository deliveryRepository, DeliveryRouteService deliveryRouteService,
                                 ApproachRouteService approachRouteService, RiderLocationStore riderLocationStore,
                                 RiderPositionService riderPositionService, DeliveryGpsProperties properties,
                                 Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryRouteService = deliveryRouteService;
        this.approachRouteService = approachRouteService;
        this.riderLocationStore = riderLocationStore;
        this.riderPositionService = riderPositionService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 실제 GPS 위치 업로드 (PUT /v1/deliveries/{id}/location). 배정된 라이더 본인 · 진행 중(배차 완료 · 픽업)만.
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
        if (!DeliveryStatus.ACTIVE.contains(delivery.getStatus())) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATUS);
        }
        riderLocationStore.stopSimulation(deliveryId);
        return report(delivery, routeOf(delivery).orElse(null), point, LocationSource.REAL);
    }

    /**
     * 위치를 계산해 저장한다. 상태 · 권한 확인은 부르는 쪽이 한다.
     *
     * @param delivery 배달 (ASSIGNED · PICKED_UP)
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
     * @param delivery   배달 (ASSIGNED · PICKED_UP)
     * @param route      경로. 모르면 null → 남은 거리 · 도착 예정 시각 없이 좌표만 저장
     * @param point      현재 좌표
     * @param source     위치 출처
     * @param travelledM 경로를 따라 이동한 거리를 알면 그 값 (가상 GPS). null 이면 좌표로 추정 (실제 GPS)
     * @return 저장한 위치
     */
    public RiderLocation report(Delivery delivery, Route route, GeoPoint point, LocationSource source,
                                Double travelledM) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        RouteLeg leg = RouteLeg.of(delivery.getStatus());
        // 이전 구간(가게까지)의 도착 · 남은 거리는 새 구간(배달지까지)에 이어 쓰지 않는다
        Optional<RiderLocation> previous = riderLocationStore.find(delivery.getId())
                .filter(found -> RouteLeg.orDefault(found.leg()) == leg);

        Integer remaining = route == null ? null
                : (int) Math.round(remainingM(route, point, travelledM, previous.orElse(null)));

        boolean wasArrived = previous.map(RiderLocation::arrived).orElse(false);
        boolean arrived = wasArrived || isWithinArrivalRadius(destinationOf(delivery, leg), remaining, point);
        OffsetDateTime arrivedAt = wasArrived ? previous.get().arrivedAt() : (arrived ? now : null);
        if (arrived && !wasArrived) {
            log.info("rider arrived: deliveryId={}, leg={}, source={}", delivery.getId(), leg, source);
        }
        // 도착 후에는 도착 예정 시각을 도착 시각으로 고정한다 (매번 "지금"으로 바뀌지 않게)
        OffsetDateTime eta = arrived ? arrivedAt
                : remaining == null ? null : now.plusSeconds(Math.round(remaining / speedMps(delivery, route, source)));

        RiderLocation location = new RiderLocation(point.lat(), point.lng(), remaining, eta,
                arrived, arrivedAt, now, source, leg);
        riderLocationStore.save(delivery.getId(), location);
        // 라이더 위치도 같이 옮긴다 → 배달을 마치면 배달지에 남아 다음 배차가 그 자리에서 출발한다
        if (delivery.getRiderId() != null) {
            riderPositionService.moveTo(delivery.getRiderId(), point);
        }
        return location;
    }

    /**
     * 지금 구간 경로를 읽는다. 배차 완료면 라이더 → 가게 경로(없으면 빈 값),
     * 픽업 후면 가게 → 배달지 경로 (아직 없지만 배달지 좌표가 있으면 직선으로 대신한다).
     *
     * @param delivery 배달
     * @return 경로. 구간 경로를 모르면 빈 값
     */
    public Optional<Route> routeOf(Delivery delivery) {
        if (RouteLeg.of(delivery.getStatus()) == RouteLeg.TO_STORE) {
            return approachRouteService.readRoute(delivery);
        }
        Optional<Route> saved = deliveryRouteService.readRoute(delivery);
        if (saved.isPresent() || delivery.getPickupPoint() == null || delivery.getDeliveryPoint() == null) {
            return saved;
        }
        return Optional.of(Route.straight(delivery.getPickupPoint(), delivery.getDeliveryPoint(),
                properties.speedKmhOf(delivery.getVehicleType())));
    }

    // 구간 끝: 가게까지 = 가게, 배달지까지 = 배달지
    private static GeoPoint destinationOf(Delivery delivery, RouteLeg leg) {
        return leg == RouteLeg.TO_STORE ? delivery.getPickupPoint() : delivery.getDeliveryPoint();
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

    // 경로상 남은 거리 또는 구간 끝까지 직선 거리가 반경 안이면 도착 (실제 GPS 가 도로 끝을 지나치는 경우 대비)
    private boolean isWithinArrivalRadius(GeoPoint destination, Integer remaining, GeoPoint point) {
        double radius = properties.arrivalRadiusM();
        if (remaining != null && remaining <= radius) {
            return true;
        }
        return destination != null && RouteGeometry.distanceM(point, destination) <= radius;
    }

    // 가상 GPS 는 설정 속도로 움직이므로 그 속도로, 실제 GPS 는 길찾기 예상 시간(교통 반영) 비율로 계산한다.
    // 길찾기는 자동차 경로라 도보 · 자전거는 실제 GPS 여도 수단별 설정 속도로 계산한다
    private double speedMps(Delivery delivery, Route route, LocationSource source) {
        VehicleType vehicleType = VehicleType.orDefault(delivery.getVehicleType());
        double configured = properties.speedKmhOf(vehicleType) * 1000 / 3600;
        if (source == LocationSource.VIRTUAL || vehicleType != VehicleType.MOTORCYCLE || route.straight()
                || route.durationS() <= 0 || route.distanceM() <= 0) {
            return configured;
        }
        return (double) route.distanceM() / route.durationS();
    }
}
