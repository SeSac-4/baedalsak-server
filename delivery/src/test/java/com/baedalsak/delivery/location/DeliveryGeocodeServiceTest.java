package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 비어 있는 좌표만 변환하고, 실패해도 예외를 던지지 않는지
 */
class DeliveryGeocodeServiceTest {

    private static final GeoPoint STORE = new GeoPoint(37.5446, 127.0559);
    private static final GeoPoint HOME = new GeoPoint(37.5410, 127.0565);

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final KakaoLocalClient kakao = mock(KakaoLocalClient.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private DeliveryGeocodeService service;

    @BeforeEach
    void setUp() {
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new DeliveryGeocodeService(repository, kakao, tx);
    }

    @Test
    void geocodesAndSavesBothAddressesWhenBothMissing() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(null, null)));
        when(kakao.geocode("서울 성동구 성수동2가 289-5")).thenReturn(Optional.of(STORE));
        when(kakao.geocode("서울 성동구 성수이로 77, 302호")).thenReturn(Optional.of(HOME));

        service.fillMissingCoordinates(1L);

        verify(repository).fillCoordinates(1L, STORE.lat(), STORE.lng(), HOME.lat(), HOME.lng());
    }

    @Test
    void savesInNewTransaction() {
        // 생성 커밋 후 리스너에서 불려도 끝난 트랜잭션에 합류하지 않게 (No active transaction 방지)
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(null, null)));
        when(kakao.geocode(anyString())).thenReturn(Optional.of(STORE));

        service.fillMissingCoordinates(1L);

        verify(tx).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void doesNotGeocodeExistingCoordinatesAgain() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(STORE, null)));
        when(kakao.geocode("서울 성동구 성수이로 77, 302호")).thenReturn(Optional.of(HOME));

        service.fillMissingCoordinates(1L);

        verify(kakao, never()).geocode("서울 성동구 성수동2가 289-5");
        verify(repository).fillCoordinates(eq(1L), isNull(), isNull(), eq(HOME.lat()), eq(HOME.lng()));
    }

    @Test
    void skipsKakaoWhenAllCoordinatesPresent() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(STORE, HOME)));

        service.fillMissingCoordinates(1L);

        verify(kakao, never()).geocode(anyString());
        verify(repository, never()).fillCoordinates(anyLong(), any(), any(), any(), any());
    }

    @Test
    void doesNotSaveWhenNeitherAddressFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(delivery(null, null)));
        when(kakao.geocode(anyString())).thenReturn(Optional.empty());

        service.fillMissingCoordinates(1L);

        verify(repository, never()).fillCoordinates(anyLong(), any(), any(), any(), any());
    }

    @Test
    void doesNotThrowOnDatabaseError() {
        when(repository.findById(1L)).thenThrow(new IllegalStateException("db down"));

        service.fillMissingCoordinates(1L);   // 예외 없이 끝나면 통과
    }

    private static Delivery delivery(GeoPoint pickup, GeoPoint destination) {
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
        return delivery;
    }
}
