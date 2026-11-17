package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 가상 GPS (배달실시간초안 8번, delivery-gps.mode=virtual 일 때만). 저장된 경로를 따라 라이더를 움직인다.
 * 배차 완료(ASSIGNED)면 라이더 위치 → 가게, 픽업 후(PICKED_UP)면 가게 → 배달지 구간을 간다.
 * 위치 = 구간 출발점에서 (지금 − 구간 시작 시각) × 속도 만큼 간 지점. 시작 시각은 Redis 에 있어
 * 파드가 다시 뜨거나 파드 2개가 동시에 돌아도 같은 위치를 계산한다 (같은 값을 두 번 써도 무해).
 * 구간 끝(가게 · 배달지)에 닿으면 그 자리에 멈추고 도착 플래그만 세운다 — 픽업 · 완료는 라이더가 직접 누른다.
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
     * 배차 수락 · 픽업 직후 그 구간의 가상 GPS 를 시작하고 출발 위치를 바로 한 번 저장한다 (첫 폴링부터 위치가 보이게).
     * real 모드면 아무것도 하지 않는다.
     *
     * @param deliveryId 배차됐거나 픽업된 배달 ID
     */
    public void start(Long deliveryId) {
        if (!properties.isVirtual()) {
            return;
        }
        try {
            Optional<Delivery> delivery = deliveryRepository.findById(deliveryId);
            if (delivery.isEmpty() || !DeliveryStatus.ACTIVE.contains(delivery.get().getStatus())) {
                return;
            }
            SimulationStart start = startOf(delivery.get());
            if (riderLocationStore.startSimulation(deliveryId, start)) {
                log.info("virtual gps started: deliveryId={}, leg={}", deliveryId, start.leg());
                step(delivery.get());
            }
        } catch (RuntimeException e) {
            log.warn("virtual gps start failed: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }

    /**
     * 갱신 주기마다 돌고 있는 가상 GPS 를 한 칸씩 움직인다. 배달 1건씩 조회하지 않고 한 번에 읽는다.
     * 진행 중(배차 완료 · 픽업)이 아니게 된 배달(완료 등)은 정리한다 — 완료 후 정리가 실패했을 때의 안전장치.
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
                if (delivery == null || !DeliveryStatus.ACTIVE.contains(delivery.getStatus())) {
                    riderLocationStore.clear(id);
                    log.info("virtual gps stopped, not active: deliveryId={}", id);
                    continue;
                }
                step(delivery);
            }
        } catch (RuntimeException e) {
            log.warn("virtual gps tick failed: cause={}", e.toString());
        }
    }

    // 경과 시간 × 속도만큼 지금 구간 경로 위를 이동한 지점을 공통 입구(report)로 저장
    private void step(Delivery delivery) {
        Optional<SimulationStart> started = riderLocationStore.findSimulationStart(delivery.getId());
        Optional<Route> route = locationReportService.routeOf(delivery);
        if (started.isEmpty() || route.isEmpty()) {
            // 시작 정보가 TTL 로 사라졌거나 좌표 · 경로가 없어 움직일 수 없음
            riderLocationStore.stopSimulation(delivery.getId());
            log.warn("virtual gps stopped: deliveryId={}, started={}, route={}",
                    delivery.getId(), started.isPresent(), route.isPresent());
            return;
        }
        SimulationStart start = started.get();
        if (start.leg() != RouteLeg.of(delivery.getStatus())) {
            // 픽업 직후 시작 처리가 빠졌으면 여기서 새 구간으로 바꾼다
            start = startOf(delivery);
            riderLocationStore.startSimulation(delivery.getId(), start);
        }
        double elapsedS = Math.max(0, Duration.between(start.startedAt(), clock.instant()).toMillis() / 1000.0);
        double travelledM = elapsedS * properties.speedKmhOf(delivery.getVehicleType()) * 1000 / 3600;
        GeoPoint point = RouteGeometry.pointAlong(route.get().path(), travelledM);
        locationReportService.report(delivery, route.get(), point, LocationSource.VIRTUAL, travelledM);
    }

    // 구간 시작 시각: 가게까지 = 배차 시각, 배달지까지 = 픽업 시각. 비어 있으면 지금
    private SimulationStart startOf(Delivery delivery) {
        RouteLeg leg = RouteLeg.of(delivery.getStatus());
        OffsetDateTime at = leg == RouteLeg.TO_STORE ? delivery.getAssignedAt() : delivery.getPickedUpAt();
        return new SimulationStart(leg, at == null ? clock.instant() : at.toInstant());
    }
}
