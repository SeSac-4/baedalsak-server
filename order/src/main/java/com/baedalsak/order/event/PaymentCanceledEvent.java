package com.baedalsak.order.event;

import java.time.OffsetDateTime;

/**
 * PaymentCanceled 이벤트 본문 (라우팅 키 payment.canceled, #127). 토스 환불 완료. 환불 상태는 payment 만 기록한다.
 */
public record PaymentCanceledEvent(
        Long paymentId,
        Long orderId,
        Integer cancelAmount,
        OffsetDateTime canceledAt
) {

    public static final String TYPE = "PaymentCanceled";
}
