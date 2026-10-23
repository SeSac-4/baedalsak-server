package com.baedalsak.order.domain;

/**
 * 주문 상태. 결제대기 → 결제완료 → 조리중 → 배달중 → 배달완료 (설계서 '상태 전이' 탭)
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    COOKING,
    DELIVERING,
    DELIVERED
}
