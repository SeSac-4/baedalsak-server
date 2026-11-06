package com.baedalsak.payment.dto.response;

import com.baedalsak.payment.domain.PaymentStatus;

/**
 * 재처리 결과 (#153). 사람이 port-forward 로 부르는 내부 API 라, 무엇을 보고 무엇을 했는지 그대로 돌려준다.
 *
 * @param before     처리 전 결제 상태
 * @param tossStatus 토스 결제 조회 상태 (DONE · CANCELED 등)
 * @param action     한 일
 * @param after      처리 후 결제 상태
 */
public record RecoverResultResponse(
    Long paymentId,
    Long orderId,
    PaymentStatus before,
    String tossStatus,
    Action action,
    PaymentStatus after
) {

  public enum Action {
    /** 토스는 이미 취소 → 환불 완료로 기록 (PaymentCanceled) */
    RECORDED_CANCELED,
    /** 토스 취소를 (같은 키 또는 새 키로) 다시 불러 성공 → 환불 완료 (PaymentCanceled) */
    CANCELED,
    /** 토스가 취소를 확정 거절 → 취소 실패 기록 (PaymentCancelFailed) */
    CANCEL_REJECTED,
    /** 같은 취소가 다른 경로에서 처리 중 (토스 IDEMPOTENT_REQUEST_PROCESSING) → 이번엔 건너뜀 */
    IN_PROGRESS,
    /** 토스 취소 결과를 모름 (응답 없음 · 5xx 등) → 진행 중 그대로, 나중에 다시 */
    UNKNOWN,
    /** 바꾸지 않음 (결제 요청됨: 토스 상태만 알려 줌) */
    NO_CHANGE,
    /** 예상 밖 토스 상태 → 바꾸지 않고 사람이 확인 */
    CHECK_NEEDED
  }
}
