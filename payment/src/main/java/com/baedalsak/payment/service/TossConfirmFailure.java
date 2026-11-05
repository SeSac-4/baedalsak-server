package com.baedalsak.payment.service;

import java.util.Set;

import com.baedalsak.payment.client.toss.TossApiException;

/**
 * 토스 결제 승인 실패 분류 (#125). HTTP 상태가 아니라 토스 '결제 승인' 에러 코드 기준.
 */
enum TossConfirmFailure {

  /** 결과를 모름: 같은 멱등 키로 다시 보낼 대상. paymentKey 는 남긴다 */
  UNKNOWN,
  /** 이미 처리된 결제: 결제 조회 1회로 결과 확인 */
  ALREADY_PROCESSED,
  /** 확정 실패: 토스가 승인하지 않았다고 확인해 줌 (카드 거절·한도 초과·CARD_PROCESSING_ERROR·NOT_FOUND_PAYMENT_SESSION 등) */
  REJECTED;

  static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";

  // 4xx 지만 처리 결과를 모르는 코드 (5xx 는 코드와 관계없이 결과 모름)
  private static final Set<String> UNKNOWN_CODES =
      Set.of("PROVIDER_ERROR", "ALREADY_PROCESSING_REQUEST", "IDEMPOTENT_REQUEST_PROCESSING");

  static TossConfirmFailure of(TossApiException e) {
    // 응답 없음 · 5xx · 에러 코드를 읽지 못한 응답은 결과를 모르는 것으로 본다
    if (e.isNoResponse() || e.getStatus() >= 500 || e.getCode() == null) {
      return UNKNOWN;
    }
    if (UNKNOWN_CODES.contains(e.getCode())) {
      return UNKNOWN;
    }
    if (ALREADY_PROCESSED_PAYMENT.equals(e.getCode())) {
      return ALREADY_PROCESSED;
    }
    return REJECTED;
  }
}
