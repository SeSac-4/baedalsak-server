package com.baedalsak.order.dto.response;

import com.baedalsak.order.domain.OrderItem;

/**
 * 주문 항목. 주문 시점의 메뉴명·단가 스냅샷
 */
public record OrderItemResponse(Long menuId, String menuName, Integer price, Integer quantity) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getMenuId(), item.getMenuName(), item.getPrice(), item.getQuantity());
    }
}
