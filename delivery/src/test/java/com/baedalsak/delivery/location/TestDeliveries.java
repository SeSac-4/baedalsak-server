package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

/**
 * 위치 테스트용 배달 (가게 → 배달지 좌표, 상태 · 라이더 지정).
 */
final class TestDeliveries {

    static final GeoPoint STORE = new GeoPoint(37.5450, 127.0560);
    static final GeoPoint HOME = new GeoPoint(37.5430, 127.0560);
    static final long CUSTOMER_ID = 1L;
    static final long OWNER_ID = 2L;
    static final long RIDER_ID = 9L;

    private TestDeliveries() {
    }

    static Delivery delivery(Long id, DeliveryStatus status) {
        Delivery delivery = Delivery.builder()
                .orderId(1042L).customerId(CUSTOMER_ID).ownerId(OWNER_ID).storeId(3L).storeName("성수 떡볶이")
                .pickupAddress("서울 성동구 성수동2가 289-5").deliveryAddress("서울 성동구 성수이로 77, 302호")
                .deliveryDong("성수동2가").customerPhone("010-1234-5678").deliveryFee(3500)
                .estimatedReadyAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(delivery, "id", id);
        ReflectionTestUtils.setField(delivery, "status", status);
        ReflectionTestUtils.setField(delivery, "riderId", status == DeliveryStatus.WAITING ? null : RIDER_ID);
        ReflectionTestUtils.setField(delivery, "pickupLat", STORE.lat());
        ReflectionTestUtils.setField(delivery, "pickupLng", STORE.lng());
        ReflectionTestUtils.setField(delivery, "deliveryLat", HOME.lat());
        ReflectionTestUtils.setField(delivery, "deliveryLng", HOME.lng());
        return delivery;
    }
}
