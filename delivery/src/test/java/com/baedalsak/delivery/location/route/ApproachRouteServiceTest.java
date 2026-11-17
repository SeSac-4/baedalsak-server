package com.baedalsak.delivery.location.route;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.location.config.DeliveryGpsProperties;
import com.baedalsak.delivery.location.geo.GeoPoint;
import com.baedalsak.delivery.location.geo.Route;
import com.baedalsak.delivery.location.kakao.KakaoDirectionsClient;
import com.baedalsak.delivery.location.tracking.RiderLocationStore;
import com.baedalsak.delivery.location.tracking.RiderPositionService;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.baedalsak.delivery.location.TestDeliveries.RIDER_ID;
import static com.baedalsak.delivery.location.TestDeliveries.STORE;
import static com.baedalsak.delivery.location.TestDeliveries.delivery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 가게까지 경로: 라이더 현재 위치 → 가게, 길찾기 실패 시 직선, 배차 완료가 아니면 건너뜀, 실패해도 예외 없음
 */
class ApproachRouteServiceTest {

    private static final GeoPoint RIDER = new GeoPoint(STORE.lat() + 0.002, STORE.lng());

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final RiderPositionService positionService = mock(RiderPositionService.class);
    private final KakaoDirectionsClient directions = mock(KakaoDirectionsClient.class);
    private final RiderLocationStore store = mock(RiderLocationStore.class);
    private final ApproachRouteService service = new ApproachRouteService(repository, positionService, directions, store,
            new DeliveryGpsProperties(DeliveryGpsProperties.Mode.VIRTUAL, Duration.ofSeconds(2),
                    new DeliveryGpsProperties.SpeedKmh(3.6, 9, 18), 30, Duration.ofMinutes(30)));

    @Test
    void savesKakaoRouteFromRiderPositionToStore() {
        givenAssigned();
        Route kakao = new Route(List.of(RIDER, new GeoPoint(37.546, 127.057), STORE), 300, 60, false);
        when(directions.findRoute(RIDER, STORE)).thenReturn(Optional.of(kakao));

        service.saveRoute(1L);

        verify(store).saveApproachRoute(1L, kakao);
    }

    @Test
    void fallsBackToStraightLineWhenDirectionsFail() {
        givenAssigned();
        when(directions.findRoute(RIDER, STORE)).thenReturn(Optional.empty());

        service.saveRoute(1L);

        ArgumentCaptor<Route> route = ArgumentCaptor.forClass(Route.class);
        verify(store).saveApproachRoute(eq(1L), route.capture());
        assertThat(route.getValue().path()).containsExactly(RIDER, STORE);
        assertThat(route.getValue().straight()).isTrue();
    }

    @Test
    void skipsWhenNotAssignedOrAlreadySaved() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(1L, DeliveryStatus.PICKED_UP)));
        service.saveRoute(1L);

        Delivery assigned = delivery(2L, DeliveryStatus.ASSIGNED);
        when(repository.findById(2L)).thenReturn(Optional.of(assigned));
        when(store.findApproachRoute(2L)).thenReturn(Optional.of(new Route(List.of(RIDER, STORE), 222, 44, true)));
        service.saveRoute(2L);

        verify(directions, never()).findRoute(any(), any());
        verify(store, never()).saveApproachRoute(anyLong(), any());
    }

    @Test
    void doesNotThrowWhenDirectionsThrow() {
        givenAssigned();
        when(directions.findRoute(RIDER, STORE)).thenThrow(new IllegalStateException("boom"));

        service.saveRoute(1L);   // 예외 없이 끝나면 통과

        verify(store, never()).saveApproachRoute(anyLong(), any());
    }

    private void givenAssigned() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(1L, DeliveryStatus.ASSIGNED)));
        when(store.findApproachRoute(1L)).thenReturn(Optional.empty());
        when(positionService.positionNear(RIDER_ID, STORE)).thenReturn(Optional.of(RIDER));
    }
}
