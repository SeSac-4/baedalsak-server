package com.baedalsak.order.dto.response;

import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderStatus;

/**
 * [내부] 주문 조회 응답 (OrderInternal, GET /internal/orders/{orderId}). payment 가 결제 요청 생성 때 호출한다.
 * totalAmount·customerId·status 는 검증용, 나머지는 PaymentApproved 페이로드·결제완료 메일용 스냅샷 (설계서 API 명세 42행).
 */
public record OrderInternalResponse(
        Integer totalAmount,
        Long customerId,
        OrderStatus status,
        Long ownerId,
        Long storeId,
        String storeName,
        String customerEmail,
        String ownerEmail
) {

    public static OrderInternalResponse from(Order order) {
        return new OrderInternalResponse(order.getTotalAmount(), order.getCustomerId(), order.getStatus(),
                order.getOwnerId(), order.getStoreId(), order.getStoreName(),
                order.getCustomerEmail(), order.getOwnerEmail());
    }
}
