package com.baedalsak.delivery.payload;

import com.baedalsak.delivery.domain.Delivery;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * 수신: OrderAccepted (order.accepted). 사장님 주문 수락 시 order 가 발행 (설계서 '이벤트 목록' 탭).
 * 조회 시 다른 서비스를 호출하지 않도록 가게·고객 정보가 스냅샷으로 들어 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderAcceptedEvent(
        Long orderId,
        Long customerId,
        String customerPhone,
        Long ownerId,
        Long storeId,
        String storeName,
        String pickupAddress,
        String deliveryAddress,
        String deliveryDong,
        Integer deliveryFee,
        String riderRequest,
        OffsetDateTime estimatedReadyAt,
        String customerEmail,
        String ownerEmail
) {
    public static final String TYPE = "OrderAccepted";

    public Delivery toEntity() {
        return Delivery.builder()
                .orderId(orderId)
                .customerId(customerId)
                .ownerId(ownerId)
                .storeId(storeId)
                .storeName(storeName)
                .pickupAddress(pickupAddress)
                .deliveryAddress(deliveryAddress)
                .deliveryDong(deliveryDong)
                .customerPhone(customerPhone)
                .customerEmail(customerEmail)
                .ownerEmail(ownerEmail)
                .riderRequest(riderRequest)
                .deliveryFee(deliveryFee)
                .estimatedReadyAt(estimatedReadyAt)
                .build();
    }
}
