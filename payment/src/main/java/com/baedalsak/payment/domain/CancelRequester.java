package com.baedalsak.payment.domain;

/** 결제 취소를 요청한 쪽 (CancelPayment requestedBy, payment_cancel.requested_by, #127) */
public enum CancelRequester {
  /* 사장님 주문 거절 */
  OWNER,
  /* 고객 주문 취소 (가게 수락 전) */
  CUSTOMER,
  /* 조정자 자동 환불 (늦게 발견한 승인 등) */
  SYSTEM
}
