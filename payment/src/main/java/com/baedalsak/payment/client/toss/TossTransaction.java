package com.baedalsak.payment.client.toss;

import java.time.OffsetDateTime;

/**
 * 토스 거래 (#154, 거래 조회 GET /v1/transactions 응답 한 줄). 결제 승인과 취소가 각각 거래 하나다.
 * 같은 결제의 승인 · 취소는 paymentKey 가 같고 transactionKey 로 구분한다. 취소 거래의 amount 는 취소 금액(양수).
 */
public record TossTransaction(
    String transactionKey,
    String paymentKey,
    String orderId,
    String status,
    Integer amount,
    OffsetDateTime transactionAt
) {

  public static final String DONE = "DONE";
  public static final String CANCELED = "CANCELED";

  public boolean isApproval() {
    return DONE.equals(status);
  }

  public boolean isCancel() {
    return CANCELED.equals(status);
  }
}
