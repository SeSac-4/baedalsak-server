package com.baedalsak.order.dto.response;

import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.order.domain.Cart;
import com.baedalsak.order.domain.CartItem;

import java.util.List;

/**
 * 장바구니 (Cart). 담기·수량 변경·항목 삭제·조회가 함께 쓴다.
 * 비어 있으면 storeId·storeName 은 null, 금액은 0, canOrder=false, cannotOrderReason=CART_EMPTY (API 명세 3.1.2).
 */
public record CartResponse(
        Long storeId,
        String storeName,
        List<CartItemResponse> items,
        int itemsAmount,
        int deliveryFee,
        int totalAmount,
        int minOrderAmount,
        boolean canOrder,
        ErrorCode cannotOrderReason
) {

    public static CartResponse empty() {
        return new CartResponse(null, null, List.of(), 0, 0, 0, 0, false, ErrorCode.CART_EMPTY);
    }

    // 최소주문금액 미달이면 주문할 수 없다 (3.1.2)
    public static CartResponse of(Cart cart, List<CartItem> items) {
        if (items.isEmpty()) {
            return empty();
        }
        int itemsAmount = items.stream().mapToInt(CartItem::amount).sum();
        boolean minOrderMet = itemsAmount >= cart.getMinOrderAmount();
        return new CartResponse(
                cart.getStoreId(),
                cart.getStoreName(),
                items.stream().map(CartItemResponse::from).toList(),
                itemsAmount,
                cart.getDeliveryFee(),
                itemsAmount + cart.getDeliveryFee(),
                cart.getMinOrderAmount(),
                minOrderMet,
                minOrderMet ? null : ErrorCode.MIN_ORDER_AMOUNT_NOT_MET
        );
    }
}
