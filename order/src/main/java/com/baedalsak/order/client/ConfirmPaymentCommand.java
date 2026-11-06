package com.baedalsak.order.client;

/**
 * payment [내부] 결제 승인 요청 (POST /internal/payments/confirm). orderId 는 DB 주문 ID.
 */
public record ConfirmPaymentCommand(
        Long orderId,
        Long customerId,
        String paymentKey,
        Integer amount
) {
}
