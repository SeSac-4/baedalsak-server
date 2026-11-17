package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.VehicleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 배차 전 예상 시간 (배차 대기 목록 정렬 · 표시용). 목록 전체를 계산하므로 카카오를 부르지 않고
 * 직선 거리 × 도로 보정 ÷ 운송수단 속도로 어림한다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryTimeEstimator {

    /** 직선 거리를 도로 거리로 어림하는 배율 (도심 평균 굴곡) */
    static final double ROAD_FACTOR = 1.3;

    private final DeliveryGpsProperties properties;
    private final Clock clock;

    /**
     * @param delivery    배차 대기 배달
     * @param rider       라이더 현재 위치. 모르면 null → 가게까지 시간은 빼고 조리 대기만 본다
     * @param vehicleType 운송수단. 모르면 null (오토바이)
     * @return 예상 시간. 가게 · 배달지 좌표가 없으면 배달 · 합계가 null
     */
    public Estimate estimate(Delivery delivery, GeoPoint rider, VehicleType vehicleType) {
        GeoPoint store = delivery.getPickupPoint();
        GeoPoint home = delivery.getDeliveryPoint();
        double speedMps = properties.speedKmhOf(vehicleType) * 1000 / 3600;

        int readyS = secondsUntil(delivery.getEstimatedReadyAt());
        Integer pickupS = rider == null || store == null ? null : travelSeconds(rider, store, speedMps);
        Integer deliveryS = store == null || home == null ? null : travelSeconds(store, home, speedMps);
        Integer totalS = deliveryS == null ? null : Math.max(readyS, pickupS == null ? 0 : pickupS) + deliveryS;
        return new Estimate(pickupS, deliveryS, totalS);
    }

    private int secondsUntil(OffsetDateTime at) {
        if (at == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(OffsetDateTime.now(clock), at).toSeconds());
    }

    private static int travelSeconds(GeoPoint from, GeoPoint to, double speedMps) {
        return (int) Math.round(RouteGeometry.distanceM(from, to) * ROAD_FACTOR / speedMps);
    }

    /**
     * @param pickupDurationS   라이더 위치 → 가게 (초). 라이더 위치를 모르면 null
     * @param deliveryDurationS 가게 → 배달지 (초). 좌표가 없으면 null
     * @param totalDurationS    max(조리 완료까지, 가게까지) + 배달 (초). 배달 시간을 모르면 null
     */
    public record Estimate(Integer pickupDurationS, Integer deliveryDurationS, Integer totalDurationS) {
    }
}
