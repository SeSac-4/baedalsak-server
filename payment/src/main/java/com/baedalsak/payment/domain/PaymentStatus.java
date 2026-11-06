package com.baedalsak.payment.domain;

public enum PaymentStatus {
  /* 결제 요청됨 */
  REQUESTED,
  /* 승인완료 */
  APPROVED,
  /* 환불 처리 중: 결제 취소 명령을 받아 토스 취소 전에 저장. 결과를 모르거나 확정 거절돼도 이 상태로 남는다 (#127) */
  CANCEL_REQUESTED,
  /* 환불 완료: 토스 전액 취소 성공 (#127) */
  CANCELED,
  /* 결제 만료: 결제창만 열고 끝났거나(조정자의 만료 호출), 같은 고객의 새 결제 요청에 밀려남 (#128) */
  EXPIRED
}
