package com.baedalsak.order.client;

/**
 * payment [내부] 결제 요청 생성 요청 (POST /internal/payments). 금액·가게·이메일은 주문 스냅샷,
 * orderName 은 토스 결제창 주문명 (100자 이하).
 */
public record CreatePaymentCommand(
        Long orderId,
        Integer amount,
        Long customerId,
        Long ownerId,
        Long storeId,
        String storeName,
        String customerEmail,
        String ownerEmail,
        String orderName
) {
}
