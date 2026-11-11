package com.baedalsak.member.notification;

/** order.accepted (발행: order) 중 문자에 필요한 값만. 필드 이름이 바뀌면 값이 null 로 들어옴 → order 팀과 맞출 것 */
public record OrderAcceptedEvent(Long orderId, Long customerId, String storeName) {
  public static final String TYPE = "OrderAccepted";
}