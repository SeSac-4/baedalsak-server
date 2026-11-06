package com.baedalsak.order.event;

/**
 * PaymentCancelFailed 이벤트 본문 (라우팅 키 payment.cancel-failed, #127). 토스가 환불을 확정 거절함. 재처리 API 로 해결한다.
 */
public record PaymentCancelFailedEvent(
        Long paymentId,
        Long orderId,
        String tossCode,
        String reason
) {

    public static final String TYPE = "PaymentCancelFailed";
}
