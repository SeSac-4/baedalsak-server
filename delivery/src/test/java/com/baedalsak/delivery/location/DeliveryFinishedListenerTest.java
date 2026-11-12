package com.baedalsak.delivery.location;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 배달 완료 후: 배달 위치 정리, 라이더를 배달지에 남김
 */
class DeliveryFinishedListenerTest {

    private final RiderLocationStore store = mock(RiderLocationStore.class);
    private final RiderPositionProvider provider = mock(RiderPositionProvider.class);
    private final DeliveryFinishedListener listener = new DeliveryFinishedListener(store, provider);

    @Test
    void clearsLocationAndSettlesRiderAtDeliveryPoint() {
        GeoPoint home = new GeoPoint(37.541, 127.0565);

        listener.onDeliveryFinished(new DeliveryFinishedEvent(1L, 9L, home));

        verify(store).clear(1L);
        verify(provider).settle(9L, home);
    }

    @Test
    void skipsSettleWhenDeliveryPointMissing() {
        listener.onDeliveryFinished(new DeliveryFinishedEvent(1L, 9L, null));

        verify(store).clear(1L);
        verify(provider, never()).settle(anyLong(), any());
    }
}
