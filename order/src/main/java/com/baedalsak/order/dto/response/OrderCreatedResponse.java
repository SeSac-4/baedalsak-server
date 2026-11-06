package com.baedalsak.order.dto.response;

import com.baedalsak.order.client.PaymentReady;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * 주문 생성 응답. 주문 상세(OrderDetail) 필드에 토스 결제창 값 payment 를 더한다 (화면 U-04).
 */
public record OrderCreatedResponse(
        @JsonUnwrapped OrderDetailResponse order,
        PaymentReady payment
) {

    public static OrderCreatedResponse of(OrderDetailResponse order, PaymentReady payment) {
        return new OrderCreatedResponse(order, payment);
    }
}
