package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 픽업 후 경로 저장: 길찾기 결과 저장, 실패 시 직선 대체, 좌표 없으면 건너뜀, 저장된 경로 읽기
 */
class DeliveryRouteServiceTest {

    private static final GeoPoint STORE = new GeoPoint(37.5446, 127.0559);
    private static final GeoPoint MIDDLE = new GeoPoint(37.5430, 127.0562);
    private static final GeoPoint HOME = new GeoPoint(37.5410, 127.0565);

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final DeliveryGeocodeService geocodeService = mock(DeliveryGeocodeService.class);
    private final KakaoDirectionsClient directions = mock(KakaoDirectionsClient.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private DeliveryRouteService service;

    @BeforeEach
    void setUp() {
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        DeliveryGpsProperties gps = new DeliveryGpsProperties(DeliveryGpsProperties.Mode.VIRTUAL,
                Duration.ofSeconds(2), new DeliveryGpsProperties.SpeedKmh(3.6, 9, 18), 30, Duration.ofMinutes(30));
        service = new DeliveryRouteService(repository, geocodeService, directions, gps, jsonMapper, tx);
    }

    @Test
    void savesKakaoRouteInNewTransaction() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(STORE, HOME, null)));
        when(directions.findRoute(STORE, HOME)).thenReturn(Optional.of(new Route(List.of(STORE, MIDDLE, HOME), 512, 95, false)));

        service.saveRoute(1L);

        verify(geocodeService).fillMissingCoordinates(1L);
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(repository).saveRouteIfAbsent(eq(1L), path.capture(), eq(512), eq(95));
        assertThat(path.getValue()).isEqualTo(
                "[{\"lat\":37.5446,\"lng\":127.0559},{\"lat\":37.543,\"lng\":127.0562},{\"lat\":37.541,\"lng\":127.0565}]");
        verify(tx).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void fallsBackToStraightLineWhenDirectionsFail() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(STORE, HOME, null)));
        when(directions.findRoute(STORE, HOME)).thenReturn(Optional.empty());

        service.saveRoute(1L);

        // 직선 약 403m, 18km/h(5m/s) → 약 81초
        int distance = (int) Math.round(RouteGeometry.distanceM(STORE, HOME));
        verify(repository).saveRouteIfAbsent(eq(1L),
                eq("[{\"lat\":37.5446,\"lng\":127.0559},{\"lat\":37.541,\"lng\":127.0565}]"),
                eq(distance), eq((int) Math.round(distance / 5.0)));
    }

    @Test
    void skipsWhenCoordinatesMissing() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(STORE, null, null)));

        service.saveRoute(1L);

        verify(directions, never()).findRoute(any(), any());
        verify(repository, never()).saveRouteIfAbsent(anyLong(), anyString(), any(), any());
    }

    @Test
    void skipsWhenRouteAlreadySaved() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(STORE, HOME, "[]")));

        service.saveRoute(1L);

        verify(directions, never()).findRoute(any(), any());
    }

    @Test
    void doesNotThrowWhenRepositoryFails() {
        when(repository.findById(1L)).thenThrow(new IllegalStateException("db down"));

        service.saveRoute(1L);   // 예외 없이 끝나면 통과
    }

    @Test
    void readsSavedRoute() {
        Delivery saved = delivery(STORE, HOME,
                "[{\"lat\":37.5446,\"lng\":127.0559},{\"lat\":37.543,\"lng\":127.0562},{\"lat\":37.541,\"lng\":127.0565}]");
        ReflectionTestUtils.setField(saved, "routeDistanceM", 512);
        ReflectionTestUtils.setField(saved, "routeDurationS", 95);

        Route route = service.readRoute(saved).orElseThrow();

        assertThat(route.path()).containsExactly(STORE, MIDDLE, HOME);
        assertThat(route.distanceM()).isEqualTo(512);
        assertThat(route.straight()).isFalse();
    }

    @Test
    void readReturnsEmptyForMissingOrBrokenRoute() {
        assertThat(service.readRoute(delivery(STORE, HOME, null))).isEmpty();
        assertThat(service.readRoute(delivery(STORE, HOME, "[{\"lat\":1}]"))).isEmpty();
    }

    private static Delivery delivery(GeoPoint pickup, GeoPoint destination, String routePath) {
        Delivery delivery = Delivery.builder()
                .orderId(1042L).customerId(1L).ownerId(2L).storeId(3L).storeName("성수 떡볶이")
                .pickupAddress("서울 성동구 성수동2가 289-5").deliveryAddress("서울 성동구 성수이로 77, 302호")
                .deliveryDong("성수동2가").customerPhone("010-1234-5678").deliveryFee(3500)
                .estimatedReadyAt(OffsetDateTime.now())
                .build();
        if (pickup != null) {
            ReflectionTestUtils.setField(delivery, "pickupLat", pickup.lat());
            ReflectionTestUtils.setField(delivery, "pickupLng", pickup.lng());
        }
        if (destination != null) {
            ReflectionTestUtils.setField(delivery, "deliveryLat", destination.lat());
            ReflectionTestUtils.setField(delivery, "deliveryLng", destination.lng());
        }
        ReflectionTestUtils.setField(delivery, "routePath", routePath);
        return delivery;
    }
}
