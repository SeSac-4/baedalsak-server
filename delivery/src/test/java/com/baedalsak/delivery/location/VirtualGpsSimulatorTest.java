package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.domain.VehicleType;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.baedalsak.delivery.location.TestDeliveries.HOME;
import static com.baedalsak.delivery.location.TestDeliveries.STORE;
import static com.baedalsak.delivery.location.TestDeliveries.delivery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 가상 GPS: 경과 시간 × 속도 위치, 도착점에서 멈춤, 상태가 바뀐 배달 정리, real 모드에서는 동작 안 함
 */
class VirtualGpsSimulatorTest {

    private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");
    private static final Route ROUTE = new Route(List.of(STORE, HOME), 222, 100, false);

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final LocationReportService reportService = mock(LocationReportService.class);
    private final RiderLocationStore store = mock(RiderLocationStore.class);

    @Test
    void movesAlongRouteByElapsedTimeTimesSpeed() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);
        givenRunning(delivery, NOW.minusSeconds(20));

        simulator(DeliveryGpsProperties.Mode.VIRTUAL).tick();

        // 20초 × 5m/s = 100m 이동
        GeoPoint point = reportedPoint(delivery);
        assertThat(RouteGeometry.distanceM(STORE, point)).isCloseTo(100, within(0.5));
    }

    @Test
    void walkingMovesSlowerThanMotorcycle() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);
        ReflectionTestUtils.setField(delivery, "vehicleType", VehicleType.WALK);
        givenRunning(delivery, NOW.minusSeconds(20));

        simulator(DeliveryGpsProperties.Mode.VIRTUAL).tick();

        // 20초 × 1m/s = 20m 이동
        GeoPoint point = reportedPoint(delivery);
        assertThat(RouteGeometry.distanceM(STORE, point)).isCloseTo(20, within(0.5));
    }

    @Test
    void staysAtDestinationAfterRouteEnds() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);
        givenRunning(delivery, NOW.minusSeconds(600));

        simulator(DeliveryGpsProperties.Mode.VIRTUAL).tick();

        assertThat(reportedPoint(delivery)).isEqualTo(HOME);
    }

    @Test
    void clearsDeliveryThatIsNoLongerPickedUp() {
        Delivery delivered = delivery(1L, DeliveryStatus.DELIVERED);
        when(store.activeSimulations()).thenReturn(Set.of(1L));
        when(repository.findAllById(Set.of(1L))).thenReturn(List.of(delivered));

        simulator(DeliveryGpsProperties.Mode.VIRTUAL).tick();

        verify(store).clear(1L);
        verify(reportService, never()).report(any(), any(), any(), any(), any());
    }

    @Test
    void stopsWhenStartTimeExpired() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);
        when(store.activeSimulations()).thenReturn(Set.of(1L));
        when(repository.findAllById(Set.of(1L))).thenReturn(List.of(delivery));
        when(store.findSimulationStart(1L)).thenReturn(Optional.empty());
        when(reportService.routeOf(delivery)).thenReturn(Optional.of(ROUTE));

        simulator(DeliveryGpsProperties.Mode.VIRTUAL).tick();

        verify(store).stopSimulation(1L);
    }

    @Test
    void startUsesPickupTimeAndReportsFirstPoint() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);
        Instant pickedUpAt = NOW.minusSeconds(3);
        ReflectionTestUtils.setField(delivery, "pickedUpAt", OffsetDateTime.ofInstant(pickedUpAt, ZoneId.of("Asia/Seoul")));
        when(repository.findById(1L)).thenReturn(Optional.of(delivery));
        when(store.startSimulation(1L, pickedUpAt)).thenReturn(true);
        when(store.findSimulationStart(1L)).thenReturn(Optional.of(pickedUpAt));
        when(reportService.routeOf(delivery)).thenReturn(Optional.of(ROUTE));

        simulator(DeliveryGpsProperties.Mode.VIRTUAL).start(1L);

        verify(store).startSimulation(1L, pickedUpAt);
        verify(reportService).report(eq(delivery), eq(ROUTE), any(), eq(LocationSource.VIRTUAL), any());
    }

    @Test
    void doesNothingInRealMode() {
        VirtualGpsSimulator simulator = simulator(DeliveryGpsProperties.Mode.REAL);

        simulator.start(1L);
        simulator.tick();

        verify(store, never()).startSimulation(anyLong(), any());
        verify(store, never()).activeSimulations();
    }

    private void givenRunning(Delivery delivery, Instant startedAt) {
        when(store.activeSimulations()).thenReturn(Set.of(delivery.getId()));
        when(repository.findAllById(Set.of(delivery.getId()))).thenReturn(List.of(delivery));
        when(store.findSimulationStart(delivery.getId())).thenReturn(Optional.of(startedAt));
        when(reportService.routeOf(delivery)).thenReturn(Optional.of(ROUTE));
    }

    private GeoPoint reportedPoint(Delivery delivery) {
        ArgumentCaptor<GeoPoint> point = ArgumentCaptor.forClass(GeoPoint.class);
        verify(reportService).report(eq(delivery), eq(ROUTE), point.capture(), eq(LocationSource.VIRTUAL), any());
        return point.getValue();
    }

    private VirtualGpsSimulator simulator(DeliveryGpsProperties.Mode mode) {
        DeliveryGpsProperties gps = new DeliveryGpsProperties(mode, Duration.ofSeconds(2), new DeliveryGpsProperties.SpeedKmh(3.6, 9, 18), 30,
                Duration.ofMinutes(30));
        return new VirtualGpsSimulator(repository, reportService, store, gps, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }
}
