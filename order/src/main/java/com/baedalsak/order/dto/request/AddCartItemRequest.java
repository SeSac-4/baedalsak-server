package com.baedalsak.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 장바구니 담기 요청 (POST /v1/cart/items). store 내부 메뉴 조회가 가게 단위라 storeId 도 받는다.
 */
public record AddCartItemRequest(
        @NotNull(message = "가게를 선택해 주세요.")
        Long storeId,

        @NotNull(message = "메뉴를 선택해 주세요.")
        Long menuId,

        @NotNull(message = "수량을 입력해 주세요.")
        @Positive(message = "수량은 1개 이상이어야 합니다.")
        Integer quantity
) {
}
