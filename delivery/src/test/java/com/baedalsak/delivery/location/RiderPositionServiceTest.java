package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;
import java.util.random.RandomGenerator;

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
 * 라이더 현재 위치: 저장된 값 우선, 없으면 가게 바로 주변(50~300m)에 둠, real 모드는 읽기만
 */
class RiderPositionServiceTest {

    private static final long RIDER_ID = 9L;
    private static final GeoPoint SAVED = new GeoPoint(37.55, 127.05);

    private final RiderLocationStore store = mock(RiderLocationStore.class);
    private final DeliveryRepository repository = mock(DeliveryRepository.class);

    @Test
    void returnsSavedPositionWithoutPlacingNewOne() {
        when(store.findRiderPosition(RIDER_ID)).thenReturn(Optional.of(SAVED));

        assertThat(service(DeliveryGpsProperties.Mode.VIRTUAL, 0.5).myPosition(RIDER_ID)).contains(SAVED);

        verify(store, never()).placeRiderPositionIfAbsent(anyLong(), any());
    }

    @Test
    void placesNewRiderNearOldestWaitingStore() {
        when(store.findRiderPosition(RIDER_ID)).thenReturn(Optional.empty());
        when(repository.findFirstByStatusAndPickupLatIsNotNullOrderByCreatedAtAscIdAsc(DeliveryStatus.WAITING))
                .thenReturn(Optional.of(delivery(1L, DeliveryStatus.WAITING)));
        when(store.placeRiderPositionIfAbsent(eq(RIDER_ID), any())).thenAnswer(call -> Optional.of(call.getArgument(1)));

        GeoPoint placed = service(DeliveryGpsProperties.Mode.VIRTUAL, 0.5).myPosition(RIDER_ID).orElseThrow();

        // 0.5 → 50 + 0.5 × 250 = 175m
        assertThat(RouteGeometry.distanceM(STORE, placed)).isCloseTo(175, within(1.0));
    }

    @Test
    void placedPositionStaysWithinStoreRadius() {
        when(store.findRiderPosition(RIDER_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<GeoPoint> nearest = ArgumentCaptor.forClass(GeoPoint.class);

        service(DeliveryGpsProperties.Mode.VIRTUAL, 0.0).positionNear(RIDER_ID, STORE);
        verify(store).placeRiderPositionIfAbsent(eq(RIDER_ID), nearest.capture());
        assertThat(RouteGeometry.distanceM(STORE, nearest.getValue()))
                .isCloseTo(RiderPositionService.START_MIN_RADIUS_M, within(1.0));
    }

    @Test
    void returnsEmptyWhenNoWaitingStoreToPlaceNear() {
        when(store.findRiderPosition(RIDER_ID)).thenReturn(Optional.empty());
        when(repository.findFirstByStatusAndPickupLatIsNotNullOrderByCreatedAtAscIdAsc(DeliveryStatus.WAITING))
                .thenReturn(Optional.empty());

        assertThat(service(DeliveryGpsProperties.Mode.VIRTUAL, 0.5).myPosition(RIDER_ID)).isEmpty();
    }

    @Test
    void realModeOnlyReadsSavedPosition() {
        when(store.findRiderPosition(RIDER_ID)).thenReturn(Optional.empty());

        RiderPositionService service = service(DeliveryGpsProperties.Mode.REAL, 0.5);

        assertThat(service.myPosition(RIDER_ID)).isEmpty();
        assertThat(service.positionNear(RIDER_ID, STORE)).isEmpty();
        verify(store, never()).placeRiderPositionIfAbsent(anyLong(), any());
    }

    private RiderPositionService service(DeliveryGpsProperties.Mode mode, double randomValue) {
        DeliveryGpsProperties gps = new DeliveryGpsProperties(mode, Duration.ofSeconds(2),
                new DeliveryGpsProperties.SpeedKmh(3.6, 9, 18), 30, Duration.ofMinutes(30));
        return new RiderPositionService(store, repository, gps, new FixedRandom(randomValue));
    }

    // 거리 · 방향 모두 같은 값을 돌려주는 난수
    private record FixedRandom(double value) implements RandomGenerator {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }
}
