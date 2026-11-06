package com.baedalsak.payment.service;

import com.baedalsak.payment.domain.Payment;

/**
 * 결제 요청을 만들 때 저장할 값 (#128). 고객 API(order 조회 응답)와 내부 API(조정자가 넘긴 값) 둘 다 이 값으로 만든다.
 */
public record PaymentDraft(
    Long orderId,
    Integer amount,
    Long customerId,
    Long ownerId,
    Long storeId,
    String storeName,
    String customerEmail,
    String ownerEmail
) {

  Payment toPayment(String tossOrderId) {
    return Payment.request(orderId, customerId, ownerId, storeId, storeName, customerEmail, ownerEmail,
        tossOrderId, amount);
  }
}
