package com.baedalsak.store.dto.response;

import com.baedalsak.store.domain.Store;

/**
 * [내부] 가게 조회 응답 (StoreInternal). order 가 장바구니 담기·주문 생성 때 영업 여부·최소주문금액·배달비를 검증하고,
 * 가게명·픽업 주소·사장님 ID 를 주문에 스냅샷한다.
 */
public record StoreInternalResponse(
        String name,
        String address,
        Long ownerId,
        Integer deliveryFee,
        Integer minOrderAmount,
        boolean isOpenNow
) {

    public static StoreInternalResponse of(Store store, boolean isOpenNow) {
        return new StoreInternalResponse(store.getName(), store.getAddress(), store.getOwnerId(),
                store.getDeliveryFee(), store.getMinOrderAmount(), isOpenNow);
    }
}
