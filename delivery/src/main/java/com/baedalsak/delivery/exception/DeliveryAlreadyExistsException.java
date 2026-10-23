package com.baedalsak.delivery.exception;

/**
 * 같은 주문의 배달 요청이 이미 있음 (order_id UNIQUE, 주문당 1건).
 */
public class DeliveryAlreadyExistsException extends RuntimeException {

    public DeliveryAlreadyExistsException(Long orderId) {
        super("delivery already exists: orderId=" + orderId);
    }
}
