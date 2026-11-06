package com.baedalsak.order.client;

/**
 * payment [내부] 결제 요청 생성 응답. 프론트가 토스 결제창을 여는 값이다.
 */
public record PaymentReady(
        Long paymentId,
        String tossOrderId,
        String orderName,
        Integer amount
) {
}
