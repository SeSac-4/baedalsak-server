package com.baedalsak.payment.client.toss;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 토스 Payment 객체 중 취소 판단에 쓰는 값 (#127). 결제 취소(POST /v1/payments/{paymentKey}/cancel)와
 * 결제 조회(GET /v1/payments/{paymentKey}) 응답이 같은 Payment 객체다.
 * status: 전액 취소면 CANCELED, cancels: 취소 거래 목록 (거래마다 transactionKey)
 */
public record TossCancelResponse(
    String paymentKey,
    String orderId,
    String status,
    Integer totalAmount,
    List<Cancel> cancels
) {

  public static final String CANCELED = "CANCELED";

  public record Cancel(Integer cancelAmount, OffsetDateTime canceledAt, String transactionKey, String cancelStatus) {
  }

  public boolean isCanceled() {
    return CANCELED.equals(status);
  }

  // 가장 최근 취소 거래 (전액 취소면 1건). 없으면 null
  public Cancel lastCancel() {
    return cancels == null || cancels.isEmpty() ? null : cancels.get(cancels.size() - 1);
  }
}
