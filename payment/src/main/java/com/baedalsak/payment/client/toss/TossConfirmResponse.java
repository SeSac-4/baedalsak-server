package com.baedalsak.payment.client.toss;

import java.time.OffsetDateTime;

/**
 * 토스 결제 승인 응답 (POST /v1/payments/confirm) 중 payment 테이블에 저장하는 값만 받는다.
 * method: 카드·간편결제 등, receipt.url: 영수증 링크 (5.4.1)
 */
public record TossConfirmResponse(
        String paymentKey,
        String orderId,
        String method,
        OffsetDateTime approvedAt,
        Receipt receipt
) {

    public record Receipt(String url) {
    }

    public String receiptUrl() {
        return receipt == null ? null : receipt.url();
    }
}
