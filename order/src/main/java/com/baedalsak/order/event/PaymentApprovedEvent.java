package com.baedalsak.order.event;

import java.time.OffsetDateTime;

/**
 * PaymentApproved 이벤트 본문 (라우팅 키 payment.approved, 설계서 '이벤트 목록' 탭). 결제 승인 (5.2.1). PENDING_PAYMENT → PAID
 */
public record PaymentApprovedEvent(
        Long paymentId,
        Long orderId,
        Long customerId,
        Long ownerId,
        Long storeId,
        String storeName,
        Integer amount,
        OffsetDateTime approvedAt,
        String customerEmail,
        String ownerEmail
) {

    public static final String TYPE = "PaymentApproved";
}
