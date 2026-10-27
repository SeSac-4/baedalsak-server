package com.baedalsak.payment.client.toss;

import java.time.OffsetDateTime;

/**
 * 토스 결제 승인 응답 (POST /v1/payments/confirm) 중 payment 테이블에 저장하는 값만 받는다.
 * method: 카드·간편결제 등, receipt.url: 영수증 링크 (5.4.1)
 * card: 카드로 결제했을 때만 온다 (간편결제에 등록된 카드 포함). 간편결제 계좌·포인트, 계좌이체는 null
 */
public record TossConfirmResponse(
        String paymentKey,
        String orderId,
        String method,
        OffsetDateTime approvedAt,
        Receipt receipt,
        Card card
) {

    public record Receipt(String url) {
    }

    // approveNo: 카드사 승인 번호, 최대 8자
    public record Card(String approveNo) {
    }

    public String receiptUrl() {
        return receipt == null ? null : receipt.url();
    }

    // 카드 결제가 아니면 승인번호가 없다
    public String approvalNumber() {
        return card == null ? null : card.approveNo();
    }
}
