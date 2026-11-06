package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.baedalsak.delivery.location.TestDeliveries.HOME;
import static com.baedalsak.delivery.location.TestDeliveries.RIDER_ID;
import static com.baedalsak.delivery.location.TestDeliveries.STORE;
import static com.baedalsak.delivery.location.TestDeliveries.delivery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 위치 공통 입구: 남은 거리 · ETA, 도착 판정(반경 · 유지), 실제 GPS 권한 · 상태 검사
 */
class LocationReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");
    // 가게 → 배달지 남쪽 직선 약 222m, 길찾기 예상 100초 (2.22m/s)
    private static final Route ROUTE = new Route(List.of(STORE, HOME), 222, 100, false);

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final DeliveryRouteService routeService = mock(DeliveryRouteService.class);
    private final RiderLocationStore store = mock(RiderLocationStore.class);
    private LocationReportService service;

    @BeforeEach
    void setUp() {
        // 18km/h = 5m/s, 도착 반경 30m
        DeliveryGpsProperties gps = new DeliveryGpsProperties(DeliveryGpsProperties.Mode.VIRTUAL,
                Duration.ofSeconds(2), 18, 30, Duration.ofMinutes(30));
        service = new LocationReportService(repository, routeService, store, gps,
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
        when(store.find(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void virtualEtaUsesConfiguredSpeed() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);

        RiderLocation location = service.report(delivery, ROUTE, STORE, LocationSource.VIRTUAL);

        int remaining = (int) Math.round(RouteGeometry.distanceM(STORE, HOME));
        assertThat(location.remainingDistanceM()).isEqualTo(remaining);
        assertThat(location.etaAt()).isEqualTo(now().plusSeconds(Math.round(remaining / 5.0)));
        assertThat(location.arrived()).isFalse();
        assertThat(location.source()).isEqualTo(LocationSource.VIRTUAL);
        verify(store).save(eq(1L), eq(location));
    }

    @Test
    void realEtaUsesDirectionsPace() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);

        RiderLocation location = service.report(delivery, ROUTE, STORE, LocationSource.REAL);

        // 길찾기 222m / 100초 비율로 남은 거리를 시간으로 바꾼다
        assertThat(location.etaAt()).isEqualTo(
                now().plusSeconds(Math.round(location.remainingDistanceM() / (222.0 / 100))));
    }

    @Test
    void marksArrivedWithinRadius() {
        GeoPoint nearHome = new GeoPoint(HOME.lat() + 0.0002, HOME.lng());   // 약 22m 전

        RiderLocation location = service.report(delivery(1L, DeliveryStatus.PICKED_UP), ROUTE, nearHome,
                LocationSource.VIRTUAL);

        assertThat(location.arrived()).isTrue();
        assertThat(location.arrivedAt()).isEqualTo(now());
    }

    @Test
    void keepsFirstArrivalTimeAfterArrived() {
        OffsetDateTime firstArrival = now().minusMinutes(1);
        when(store.find(1L)).thenReturn(Optional.of(new RiderLocation(HOME.lat(), HOME.lng(), 0, now(),
                true, firstArrival, firstArrival, LocationSource.REAL)));
        // 도착 후 GPS 가 조금 튀어 반경 밖으로 나가도 도착은 유지
        GeoPoint drifted = new GeoPoint(HOME.lat() + 0.0005, HOME.lng());

        RiderLocation location = service.report(delivery(1L, DeliveryStatus.PICKED_UP), ROUTE, drifted,
                LocationSource.REAL);

        assertThat(location.arrived()).isTrue();
        assertThat(location.arrivedAt()).isEqualTo(firstArrival);
    }

    @Test
    void savesPointWithoutEtaWhenRouteUnknown() {
        RiderLocation location = service.report(delivery(1L, DeliveryStatus.PICKED_UP), null, STORE,
                LocationSource.REAL);

        assertThat(location.remainingDistanceM()).isNull();
        assertThat(location.etaAt()).isNull();
        assertThat(location.lat()).isEqualTo(STORE.lat());
    }

    @Test
    void realReportStopsSimulationAndSaves() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);
        when(repository.findById(1L)).thenReturn(Optional.of(delivery));
        when(routeService.readRoute(delivery)).thenReturn(Optional.of(ROUTE));

        RiderLocation location = service.reportReal(1L, RIDER_ID, STORE);

        verify(store).stopSimulation(1L);
        verify(store).save(1L, location);
        assertThat(location.source()).isEqualTo(LocationSource.REAL);
    }

    @Test
    void realReportRejectsOtherRider() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(1L, DeliveryStatus.PICKED_UP)));

        assertThatThrownBy(() -> service.reportReal(1L, 777L, STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(store, never()).save(anyLong(), any());
    }

    @Test
    void realReportRejectsBeforePickup() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(1L, DeliveryStatus.ASSIGNED)));

        assertThatThrownBy(() -> service.reportReal(1L, RIDER_ID, STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DELIVERY_INVALID_STATUS));
    }

    @Test
    void realReportRejectsUnknownDelivery() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportReal(1L, RIDER_ID, STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DELIVERY_NOT_FOUND));
    }

    @Test
    void routeOfFallsBackToStraightLineWhenNotSaved() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);
        when(routeService.readRoute(delivery)).thenReturn(Optional.empty());

        Route route = service.routeOf(delivery).orElseThrow();

        assertThat(route.path()).containsExactly(STORE, HOME);
        assertThat(route.straight()).isTrue();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul"));
    }

    @Test
    void virtualRemainingUsesTravelledDistance() {
        RiderLocation location = service.report(delivery(1L, DeliveryStatus.PICKED_UP), ROUTE, STORE,
                LocationSource.VIRTUAL, 100.0);

        double length = RouteGeometry.lengthM(ROUTE.path());
        assertThat(location.remainingDistanceM()).isEqualTo((int) Math.round(length - 100));
    }

    @Test
    void realRemainingDoesNotJumpBackBeyondTolerance() {
        // 직전에 50m 남음 → 출발점 근처로 튄 GPS 는 지나온 구간이라 보지 않고, 남은 구간 중 가장 가까운 곳으로 본다
        when(store.find(1L)).thenReturn(Optional.of(new RiderLocation(HOME.lat(), HOME.lng(), 50, now(),
                false, null, now(), LocationSource.REAL)));

        RiderLocation location = service.report(delivery(1L, DeliveryStatus.PICKED_UP), ROUTE, STORE,
                LocationSource.REAL);

        assertThat(location.remainingDistanceM()).isLessThanOrEqualTo(50 + (int) LocationReportService.BACKTRACK_TOLERANCE_M);
    }

    @Test
    void etaFreezesAtArrivalTime() {
        OffsetDateTime firstArrival = now().minusMinutes(2);
        when(store.find(1L)).thenReturn(Optional.of(new RiderLocation(HOME.lat(), HOME.lng(), 0, firstArrival,
                true, firstArrival, firstArrival, LocationSource.VIRTUAL)));

        RiderLocation location = service.report(delivery(1L, DeliveryStatus.PICKED_UP), ROUTE, HOME,
                LocationSource.VIRTUAL, 1000.0);

        assertThat(location.etaAt()).isEqualTo(firstArrival);
    }
}
