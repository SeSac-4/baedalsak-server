package com.baedalsak.order.client;

import java.time.OffsetDateTime;

/**
 * payment [내부] 결제 요청 만료 응답 1건 (POST /internal/payments/expire).
 * EXPIRED: 만료함(또는 이미 만료) / APPROVED: 승인돼 있음 → 10분 규칙 적용 / NONE: 이 주문의 결제가 없음
 */
public record PaymentExpireResult(
        Long orderId,
        Result result,
        OffsetDateTime approvedAt
) {

    public enum Result {
        EXPIRED,
        APPROVED,
        NONE
    }
}
