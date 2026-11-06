package com.baedalsak.payment.dto.response;

import java.time.OffsetDateTime;

/**
 * [내부] 결제 요청 만료 결과 1건 (#128).
 * EXPIRED: 만료함(또는 이미 만료) / APPROVED: 승인돼 있음(만료 때 토스 승인을 발견하면 승인으로 기록) → 조정자가 10분 규칙 적용 /
 * NONE: 이 주문의 결제가 없음
 */
public record ExpireResultResponse(
    Long orderId,
    Result result,
    OffsetDateTime approvedAt
) {

  public enum Result {
    EXPIRED,
    APPROVED,
    NONE
  }

  public static ExpireResultResponse expired(Long orderId) {
    return new ExpireResultResponse(orderId, Result.EXPIRED, null);
  }

  public static ExpireResultResponse approved(Long orderId, OffsetDateTime approvedAt) {
    return new ExpireResultResponse(orderId, Result.APPROVED, approvedAt);
  }

  public static ExpireResultResponse none(Long orderId) {
    return new ExpireResultResponse(orderId, Result.NONE, null);
  }
}
