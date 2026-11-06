package com.baedalsak.payment.domain;

/** 결제 취소 요청 한 건의 진행 상태 (payment_cancel.status, #127). payment.status 와 값 집합이 다르다 */
public enum PaymentCancelStatus {
  /* 취소 진행 중: 토스 결과를 아직 모름. 재시도를 다 써도 이 상태로 남고 같은 멱등 키를 유지한다 */
  PENDING,
  /* 취소 완료: 토스 취소 성공 */
  DONE,
  /* 취소 실패: 토스가 확정 거절 (다시 보내도 같은 결과). 다시 취소는 재처리 API 몫 */
  FAILED
}
