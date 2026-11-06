package com.baedalsak.order.client;

import java.time.OffsetDateTime;

/**
 * payment [내부] 결제 상태 조회 응답 1건 (GET /internal/payments).
 * status 는 문자열로 받는다. payment 에 상태가 추가돼도 파싱이 실패하지 않고 점검이 예상 밖 상태로 알린다.
 */
public record PaymentStatusResult(
        Long orderId,
        Long paymentId,
        String status,
        Integer amount,
        OffsetDateTime approvedAt
) {

    public static final String REQUESTED = "REQUESTED";
    public static final String APPROVED = "APPROVED";
    public static final String EXPIRED = "EXPIRED";
}
