package com.baedalsak.order.dto.response;

import com.baedalsak.order.domain.CartItem;

/**
 * 장바구니 항목. 메뉴명·가격은 담을 때 스냅샷
 */
public record CartItemResponse(Long menuId, String menuName, Integer price, Integer quantity) {

    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.getMenuId(), item.getMenuName(), item.getPrice(), item.getQuantity());
    }
}
