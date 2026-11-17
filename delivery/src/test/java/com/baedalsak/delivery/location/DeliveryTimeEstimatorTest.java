package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static com.baedalsak.delivery.location.TestDeliveries.HOME;
import static com.baedalsak.delivery.location.TestDeliveries.STORE;
import static com.baedalsak.delivery.location.TestDeliveries.delivery;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배차 전 예상 시간: 조리 대기 · 가게까지 중 큰 값 + 배달, 좌표 · 위치를 모를 때
 */
class DeliveryTimeEstimatorTest {

    private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 오토바이 18km/h = 5m/s. STORE → HOME 직선 약 222m × 1.3 ≈ 289m → 58초
    private final DeliveryTimeEstimator estimator = new DeliveryTimeEstimator(
            new DeliveryGpsProperties(DeliveryGpsProperties.Mode.VIRTUAL, Duration.ofSeconds(2),
                    new DeliveryGpsProperties.SpeedKmh(3.6, 9, 18), 30, Duration.ofMinutes(30)),
            Clock.fixed(NOW, KST));

    @Test
    void totalUsesCookingWaitWhenLongerThanTravelToStore() {
        Delivery delivery = waiting(Duration.ofMinutes(10));

        DeliveryTimeEstimator.Estimate estimate = estimator.estimate(delivery, STORE, null);

        assertThat(estimate.pickupDurationS()).isZero();
        assertThat(estimate.deliveryDurationS()).isEqualTo(58);
        assertThat(estimate.totalDurationS()).isEqualTo(600 + 58);
    }

    @Test
    void totalUsesTravelToStoreWhenLongerThanCookingWait() {
        Delivery delivery = waiting(Duration.ZERO);
        GeoPoint farRider = new GeoPoint(STORE.lat() + 0.01, STORE.lng());   // 약 1112m × 1.3 ≈ 1446m → 289초

        DeliveryTimeEstimator.Estimate estimate = estimator.estimate(delivery, farRider, null);

        assertThat(estimate.pickupDurationS()).isEqualTo(289);
        assertThat(estimate.totalDurationS()).isEqualTo(289 + 58);
    }

    @Test
    void walkingIsSlowerThanMotorcycle() {
        Delivery delivery = waiting(Duration.ZERO);

        assertThat(estimator.estimate(delivery, STORE, VehicleType.WALK).deliveryDurationS())
                .isEqualTo(289);   // 1m/s
    }

    @Test
    void unknownRiderPositionUsesCookingWaitOnly() {
        DeliveryTimeEstimator.Estimate estimate = estimator.estimate(waiting(Duration.ofMinutes(5)), null, null);

        assertThat(estimate.pickupDurationS()).isNull();
        assertThat(estimate.totalDurationS()).isEqualTo(300 + 58);
    }

    @Test
    void unknownDeliveryPointLeavesTotalEmpty() {
        Delivery delivery = waiting(Duration.ZERO);
        ReflectionTestUtils.setField(delivery, "deliveryLat", null);

        DeliveryTimeEstimator.Estimate estimate = estimator.estimate(delivery, STORE, null);

        assertThat(estimate.deliveryDurationS()).isNull();
        assertThat(estimate.totalDurationS()).isNull();
    }

    private Delivery waiting(Duration readyIn) {
        Delivery delivery = delivery(1L, DeliveryStatus.WAITING);
        ReflectionTestUtils.setField(delivery, "estimatedReadyAt", OffsetDateTime.ofInstant(NOW.plus(readyIn), KST));
        return delivery;
    }
}
