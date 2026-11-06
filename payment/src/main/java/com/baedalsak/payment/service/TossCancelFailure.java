package com.baedalsak.payment.service;

import java.util.Set;

import com.baedalsak.payment.client.toss.TossApiException;

/**
 * 토스 결제 취소 실패 분류 (#127). HTTP 상태가 아니라 토스 '결제 취소' 에러 코드로 나눈다.
 * <ul>
 *   <li>RETRY: 결과를 모르거나 잠깐의 문제 → 같은 멱등 키로 다시 (연결 실패 · 시간 초과, 5xx, 코드를 읽지 못한 응답, PROVIDER_ERROR,
 *       FORBIDDEN_CONSECUTIVE_REQUEST, IDEMPOTENT_REQUEST_PROCESSING)</li>
 *   <li>CHECK: 이미 취소됐을 수 있음 → 토스 조회로 판단 (ALREADY_CANCELED_PAYMENT, NOT_CANCELABLE_PAYMENT(이미 취소된 경우에도 발생),
 *       ALREADY_REFUNDING_PAYMENT)</li>
 *   <li>REJECTED: 그 밖의 에러 응답 → 확정 거절 (다시 보내도 같은 결과)</li>
 * </ul>
 */
public enum TossCancelFailure {
  RETRY,
  CHECK,
  REJECTED;

  private static final Set<String> RETRY_CODES = Set.of(
      "PROVIDER_ERROR", "FORBIDDEN_CONSECUTIVE_REQUEST", "IDEMPOTENT_REQUEST_PROCESSING");
  private static final Set<String> CHECK_CODES = Set.of(
      "ALREADY_CANCELED_PAYMENT", "NOT_CANCELABLE_PAYMENT", "ALREADY_REFUNDING_PAYMENT");

  public static TossCancelFailure of(TossApiException e) {
    // 응답 없음 · 5xx · 에러 코드를 읽지 못한 응답은 결과를 모르는 것으로 본다 (#125 승인 분류와 같음)
    String code = e.getCode();
    if (e.isNoResponse() || e.getStatus() >= 500 || code == null || RETRY_CODES.contains(code)) {
      return RETRY;
    }
    if (CHECK_CODES.contains(code)) {
      return CHECK;
    }
    return REJECTED;
  }
}
