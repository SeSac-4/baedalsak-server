package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.dto.DeliveryLocationResponse;
import com.baedalsak.delivery.dto.DeliveryRouteResponse;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.baedalsak.delivery.location.TestDeliveries.HOME;
import static com.baedalsak.delivery.location.TestDeliveries.STORE;
import static com.baedalsak.delivery.location.TestDeliveries.delivery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 경로 · 위치 조회: 고객 · 사장님 · 배정된 라이더만, 위치는 픽업 중에만, 배달이 없으면 빈 값
 */
class DeliveryTrackingServiceTest {

    private static final RiderLocation LOCATION = new RiderLocation(STORE.lat(), STORE.lng(), 200,
            OffsetDateTime.now(), false, null, OffsetDateTime.now(), LocationSource.VIRTUAL, RouteLeg.TO_CUSTOMER);

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final DeliveryRouteService routeService = mock(DeliveryRouteService.class);
    private final ApproachRouteService approachRouteService = mock(ApproachRouteService.class);
    private final RiderLocationStore store = mock(RiderLocationStore.class);
    private final DeliveryTrackingService service = new DeliveryTrackingService(repository, routeService, approachRouteService, store);

    @ParameterizedTest
    @ValueSource(longs = {TestDeliveries.CUSTOMER_ID, TestDeliveries.OWNER_ID, TestDeliveries.RIDER_ID})
    void customerOwnerAndAssignedRiderSeeSameLocation(long memberId) {
        when(repository.findByOrderId(1042L)).thenReturn(Optional.of(delivery(1L, DeliveryStatus.PICKED_UP)));
        when(store.find(1L)).thenReturn(Optional.of(LOCATION));

        DeliveryLocationResponse response = service.getLocation(1042L, memberId).orElseThrow();

        assertThat(response.status()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(response.location()).isEqualTo(LOCATION);
    }

    @Test
    void rejectsOtherMember() {
        when(repository.findByOrderId(1042L)).thenReturn(Optional.of(delivery(1L, DeliveryStatus.PICKED_UP)));

        assertThatThrownBy(() -> service.getLocation(1042L, 777L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void hidesLocationWhenNotActive() {
        when(repository.findByOrderId(1042L)).thenReturn(Optional.of(delivery(1L, DeliveryStatus.DELIVERED)));

        DeliveryLocationResponse response = service.getLocation(1042L, TestDeliveries.CUSTOMER_ID).orElseThrow();

        assertThat(response.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(response.location()).isNull();
        verify(store, never()).find(anyLong());
    }

    @Test
    void returnsEmptyWhenNoDeliveryYet() {
        when(repository.findByOrderId(1042L)).thenReturn(Optional.empty());

        assertThat(service.getLocation(1042L, TestDeliveries.CUSTOMER_ID)).isEmpty();
        assertThat(service.getRoute(1042L, TestDeliveries.CUSTOMER_ID)).isEmpty();
    }

    @Test
    void returnsSavedRoute() {
        Delivery delivery = delivery(1L, DeliveryStatus.PICKED_UP);
        when(repository.findByOrderId(1042L)).thenReturn(Optional.of(delivery));
        when(routeService.readRoute(delivery)).thenReturn(Optional.of(new Route(List.of(STORE, HOME), 222, 100, false)));

        DeliveryRouteResponse response = service.getRoute(1042L, TestDeliveries.OWNER_ID).orElseThrow();
        assertThat(response.leg()).isEqualTo(RouteLeg.TO_CUSTOMER);
        assertThat(response.path()).containsExactly(STORE, HOME);
    }

    @Test
    void assignedDeliveryReturnsRiderToStoreRouteAndLocation() {
        Delivery delivery = delivery(1L, DeliveryStatus.ASSIGNED);
        GeoPoint rider = new GeoPoint(STORE.lat() + 0.002, STORE.lng());
        when(repository.findByOrderId(1042L)).thenReturn(Optional.of(delivery));
        when(approachRouteService.readRoute(delivery)).thenReturn(Optional.of(new Route(List.of(rider, STORE), 222, 44, false)));
        when(store.find(1L)).thenReturn(Optional.of(LOCATION));

        DeliveryRouteResponse route = service.getRoute(1042L, TestDeliveries.CUSTOMER_ID).orElseThrow();
        assertThat(route.leg()).isEqualTo(RouteLeg.TO_STORE);
        assertThat(route.path()).containsExactly(rider, STORE);
        assertThat(service.getLocation(1042L, TestDeliveries.CUSTOMER_ID).orElseThrow().location()).isEqualTo(LOCATION);
        verify(routeService, never()).readRoute(any());
    }

    @Test
    void waitingDeliveryHasNoRoute() {
        when(repository.findByOrderId(1042L)).thenReturn(Optional.of(delivery(1L, DeliveryStatus.WAITING)));

        assertThat(service.getRoute(1042L, TestDeliveries.CUSTOMER_ID)).isEmpty();
    }
}
