package com.baedalsak.order.dto.response;

/**
 * [내부] 가게의 진행 중 주문 수 (GET /internal/orders/active-count, #176). store 가 가게 폐업 전에 확인한다.
 */
public record ActiveOrderCountResponse(long count) {
}
