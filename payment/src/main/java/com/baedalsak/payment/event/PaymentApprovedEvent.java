package com.baedalsak.payment.event;

import java.time.OffsetDateTime;

import com.baedalsak.payment.domain.Payment;

/**
 * PaymentApproved 이벤트 본문 (라우팅 키 payment.approved, 설계서 '이벤트 목록' 탭). 결제 승인 (5.2.1).
 * 가게·수신자 값은 결제 요청 생성 때 저장한 스냅샷 (#13). 구독: order(주문 결제완료), payment 메일 큐(결제완료 메일)
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
  public static final String ROUTING_KEY = "payment.approved";

  public static PaymentApprovedEvent from(Payment payment) {
    return new PaymentApprovedEvent(payment.getId(), payment.getOrderId(), payment.getCustomerId(),
        payment.getOwnerId(), payment.getStoreId(), payment.getStoreName(), payment.getAmount(),
        payment.getApprovedAt(), payment.getCustomerEmail(), payment.getOwnerEmail());
  }
}
