package com.baedalsak.member.notification;

/**
 * payment.approved (발행: payment) 중 사장님 문자에 필요한 값만 (#217). 결제 완료 = 사장님에게는 새 주문.
 * 필드 이름이 바뀌면 값이 null 로 들어옴 → payment 팀과 맞출 것 (payment PaymentApprovedEvent)
 */
public record PaymentApprovedEvent(Long orderId, Long ownerId, String storeName, Integer amount) {
  public static final String TYPE = "PaymentApproved";
}
