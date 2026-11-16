package com.baedalsak.delivery.location;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 위치 제공자: 가상은 저장된 위치 우선 · 없으면 가게 주변 무작위 · 완료 시 배달지에 남김, 실제는 읽기만
 */
class VirtualRiderPositionProviderTest {

    private static final GeoPoint STORE = new GeoPoint(37.5446, 127.0559);
    private static final RiderPositionProperties PROPERTIES = new RiderPositionProperties(300, 1500);

    private final RiderLocationStore store = mock(RiderLocationStore.class);
    private final VirtualRiderPositionProvider provider =
            new VirtualRiderPositionProvider(store, PROPERTIES, new SplittableRandom(7));

    @Test
    void returnsSavedPositionWithoutCreatingNewOne() {
        GeoPoint saved = new GeoPoint(37.55, 127.05);
        when(store.findRiderPosition(9L)).thenReturn(Optional.of(saved));

        assertThat(provider.currentPosition(9L, STORE)).contains(saved);
        verify(store, never()).saveRiderPositionIfAbsent(anyLong(), any());
    }

    @Test
    void createsRandomPositionAroundStoreWhenAbsent() {
        when(store.findRiderPosition(9L)).thenReturn(Optional.empty());
        when(store.saveRiderPositionIfAbsent(eq(9L), any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

        GeoPoint created = provider.currentPosition(9L, STORE).orElseThrow();

        assertThat(RouteGeometry.distanceM(STORE, created)).isBetween(295.0, 1505.0);
    }

    @Test
    void randomPositionsStayWithinRadiusAndDiffer() {
        GeoPoint first = provider.randomPointAround(STORE);
        GeoPoint second = provider.randomPointAround(STORE);

        assertThat(first).isNotEqualTo(second);
        for (int i = 0; i < 200; i++) {
            assertThat(RouteGeometry.distanceM(STORE, provider.randomPointAround(STORE))).isBetween(295.0, 1505.0);
        }
    }

    @Test
    void usesPositionSavedByConcurrentRequest() {
        GeoPoint other = new GeoPoint(37.55, 127.05);
        when(store.findRiderPosition(9L)).thenReturn(Optional.empty());
        when(store.saveRiderPositionIfAbsent(eq(9L), any())).thenReturn(Optional.of(other));

        assertThat(provider.currentPosition(9L, STORE)).contains(other);
    }

    @Test
    void returnsEmptyWhenNoPositionAndNoReferencePoint() {
        when(store.findRiderPosition(9L)).thenReturn(Optional.empty());

        assertThat(provider.currentPosition(9L, null)).isEmpty();
        verify(store, never()).saveRiderPositionIfAbsent(anyLong(), any());
    }

    @Test
    void settlesRiderAtDeliveryPoint() {
        GeoPoint home = new GeoPoint(37.541, 127.0565);

        provider.settle(9L, home);

        verify(store).saveRiderPosition(9L, home);
    }

    @Test
    void realProviderOnlyReadsUploadedPosition() {
        RealRiderPositionProvider real = new RealRiderPositionProvider(store);
        when(store.findRiderPosition(9L)).thenReturn(Optional.empty());

        assertThat(real.currentPosition(9L, STORE)).isEmpty();
        real.settle(9L, STORE);
        verify(store, never()).saveRiderPositionIfAbsent(anyLong(), any());
        verify(store, never()).saveRiderPosition(anyLong(), any());
    }
}
