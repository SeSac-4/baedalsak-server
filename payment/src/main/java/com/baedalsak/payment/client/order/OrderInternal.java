package com.baedalsak.payment.client.order;

/**
 * order [내부] 주문 조회 응답 (GET /internal/orders/{orderId}).
 * totalAmount·customerId·status 는 검증용, ownerId~ownerEmail 은 결제 스냅샷 (PaymentApproved 페이로드·결제완료 메일).
 * orderName 은 토스 결제창 주문명 (order 가 "첫 메뉴명 외 N건" 으로 만들어 100자 이내로 준다). 주문 항목이 바뀌지 않아 저장하지 않는다.
 */
public record OrderInternal(
        Integer totalAmount,
        Long customerId,
        OrderStatus status,
        Long ownerId,
        Long storeId,
        String storeName,
        String customerEmail,
        String ownerEmail,
        String orderName
) {

    public enum OrderStatus {
        PENDING_PAYMENT,
        PAID,
        COOKING,
        DELIVERING,
        DELIVERED
    }

    public boolean isPendingPayment() {
        return status == OrderStatus.PENDING_PAYMENT;
    }
}
