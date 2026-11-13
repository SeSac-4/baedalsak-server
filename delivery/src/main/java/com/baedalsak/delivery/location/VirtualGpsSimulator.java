package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 가상 GPS (배달실시간초안 8번, delivery-gps.mode=virtual 일 때만). 픽업 후 저장된 경로를 따라 라이더를 움직인다.
 * 위치 = 경로 출발점에서 (지금 − 시작 시각) × 속도 만큼 간 지점. 시작 시각은 Redis 에 있어
 * 파드가 다시 뜨거나 파드 2개가 동시에 돌아도 같은 위치를 계산한다 (같은 값을 두 번 써도 무해).
 * 배달지에 닿으면 그 자리에 멈추고 도착 플래그만 세운다 — 완료는 라이더가 직접 누른다.
 */
@Slf4j
@Component
public class VirtualGpsSimulator {

    private final DeliveryRepository deliveryRepository;
    private final LocationReportService locationReportService;
    private final RiderLocationStore riderLocationStore;
    private final DeliveryGpsProperties properties;
    private final Clock clock;

    public VirtualGpsSimulator(DeliveryRepository deliveryRepository, LocationReportService locationReportService,
                               RiderLocationStore riderLocationStore, DeliveryGpsProperties properties, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.locationReportService = locationReportService;
        this.riderLocationStore = riderLocationStore;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 픽업 직후 가상 GPS 를 시작하고 가게 위치를 바로 한 번 저장한다 (첫 폴링부터 위치가 보이게).
     * real 모드면 아무것도 하지 않는다.
     *
     * @param deliveryId 픽업된 배달 ID
     */
    public void start(Long deliveryId) {
        if (!properties.isVirtual()) {
            return;
        }
        try {
            Optional<Delivery> delivery = deliveryRepository.findById(deliveryId);
            if (delivery.isEmpty() || delivery.get().getStatus() != DeliveryStatus.PICKED_UP) {
                return;
            }
            Instant startedAt = Optional.ofNullable(delivery.get().getPickedUpAt())
                    .map(at -> at.toInstant()).orElse(clock.instant());
            if (riderLocationStore.startSimulation(deliveryId, startedAt)) {
                log.info("virtual gps started: deliveryId={}", deliveryId);
                step(delivery.get());
            }
        } catch (RuntimeException e) {
            log.warn("virtual gps start failed: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }

    /**
     * 갱신 주기마다 돌고 있는 가상 GPS 를 한 칸씩 움직인다. 배달 1건씩 조회하지 않고 한 번에 읽는다.
     * 픽업 상태가 아니게 된 배달(완료 등)은 정리한다 — 완료 후 정리가 실패했을 때의 안전장치.
     */
    @Scheduled(fixedDelayString = "${delivery-gps.update-interval}")
    public void tick() {
        if (!properties.isVirtual()) {
            return;
        }
        Set<Long> ids = riderLocationStore.activeSimulations();
        if (ids.isEmpty()) {
            return;
        }
        try {
            Map<Long, Delivery> deliveries = deliveryRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Delivery::getId, Function.identity()));
            for (Long id : ids) {
                Delivery delivery = deliveries.get(id);
                if (delivery == null || delivery.getStatus() != DeliveryStatus.PICKED_UP) {
                    riderLocationStore.clear(id);
                    log.info("virtual gps stopped, not picked up: deliveryId={}", id);
                    continue;
                }
                step(delivery);
            }
        } catch (RuntimeException e) {
            log.warn("virtual gps tick failed: cause={}", e.toString());
        }
    }

    // 경과 시간 × 속도만큼 경로 위를 이동한 지점을 공통 입구(report)로 저장
    private void step(Delivery delivery) {
        Optional<Instant> startedAt = riderLocationStore.findSimulationStart(delivery.getId());
        Optional<Route> route = locationReportService.routeOf(delivery);
        if (startedAt.isEmpty() || route.isEmpty()) {
            // 시작 정보가 TTL 로 사라졌거나 좌표가 없어 움직일 수 없음
            riderLocationStore.stopSimulation(delivery.getId());
            log.warn("virtual gps stopped: deliveryId={}, started={}, route={}",
                    delivery.getId(), startedAt.isPresent(), route.isPresent());
            return;
        }
        double elapsedS = Math.max(0, Duration.between(startedAt.get(), clock.instant()).toMillis() / 1000.0);
        double travelledM = elapsedS * properties.speedKmhOf(delivery.getVehicleType()) * 1000 / 3600;
        GeoPoint point = RouteGeometry.pointAlong(route.get().path(), travelledM);
        locationReportService.report(delivery, route.get(), point, LocationSource.VIRTUAL, travelledM);
    }
}
