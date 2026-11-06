package com.baedalsak.payment.event;

/**
 * CancelPayment 명령 본문 (라우팅 키 command.payment.cancel, 설계서 '이벤트 목록' 탭, #127). 조정자(order)가 보낸다.
 * 결제는 orderId 로 찾고, paymentId 는 알면 넣는 확인용 값. 금액 · 고객은 payment 기록이 기준이라 받지 않는다(전액 취소).
 * requestedBy 는 OWNER · CUSTOMER · SYSTEM (문자열로 받아 검증 — 모르는 값이면 DLQ)
 */
public record CancelPaymentCommand(
    Long orderId,
    Long paymentId,
    String reason,
    String requestedBy
) {

  public static final String TYPE = "CancelPayment";
  public static final String ROUTING_KEY = "command.payment.cancel";
}
