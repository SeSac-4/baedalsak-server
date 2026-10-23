package com.baedalsak.order.client;

/**
 * store [내부] 가게 조회 응답 (GET /internal/stores/{storeId})
 */
public record StoreInternal(
        String name,
        String address,
        Long ownerId,
        Integer deliveryFee,
        Integer minOrderAmount,
        boolean isOpenNow
) {
}
