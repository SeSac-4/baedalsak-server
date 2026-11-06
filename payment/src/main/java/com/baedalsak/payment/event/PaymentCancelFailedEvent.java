package com.baedalsak.payment.event;

/**
 * PaymentCancelFailed 응답 (라우팅 키 payment.cancel-failed, 설계서 '이벤트 목록' 탭, #127). 토스 취소 확정 거절(다시 보내도 같은 결과).
 * 구독: order(조정자, order.payment-cancel-failed) — ERROR 로그(알림). 재처리는 payment 재처리 API
 */
public record PaymentCancelFailedEvent(
    Long paymentId,
    Long orderId,
    String tossCode,
    String reason
) {

  public static final String TYPE = "PaymentCancelFailed";
  public static final String ROUTING_KEY = "payment.cancel-failed";
}
