package com.baedalsak.payment.domain;

public enum PaymentStatus {
  /* 결제 요청됨 */
  REQUESTED,
  /* 승인완료 */
  APPROVED,
  /* 결제 만료: 결제창만 열고 끝났거나(조정자의 만료 호출), 같은 고객의 새 결제 요청에 밀려남 (#128) */
  EXPIRED
}
