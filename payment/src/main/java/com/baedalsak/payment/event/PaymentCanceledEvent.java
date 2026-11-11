package com.baedalsak.payment.event;

import java.time.OffsetDateTime;

/**
 * PaymentCanceled 응답 (라우팅 키 payment.canceled, 설계서 '이벤트 목록' 탭, #127). 토스 전액 취소 성공.
 * 구독: order(조정자, order.payment-canceled) — 보상 완료 기록, payment 메일 큐(payment.mail.payment-canceled) — 환불 완료 메일 (#180).
 * customerEmail · storeName · requestedBy 는 환불 완료 메일용 (order 는 읽지 않는다)
 */
public record PaymentCanceledEvent(
    Long paymentId,
    Long orderId,
    Integer cancelAmount,
    OffsetDateTime canceledAt,
    String customerEmail,
    String storeName,
    String requestedBy
) {

  public static final String TYPE = "PaymentCanceled";
  public static final String ROUTING_KEY = "payment.canceled";
}
