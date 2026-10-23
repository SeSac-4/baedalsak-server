package com.baedalsak.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 장바구니 수량 변경 요청 (PATCH /v1/cart/items/{menuId})
 */
public record UpdateCartItemRequest(
        @NotNull(message = "수량을 입력해 주세요.")
        @Positive(message = "수량은 1개 이상이어야 합니다.")
        Integer quantity
) {
}
