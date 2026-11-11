package com.baedalsak.member.notification;

/** delivery.completed (발행: delivery) 중 문자에 필요한 값만 */
public record DeliveryCompletedEvent(Long orderId, Long customerId) {
  public static final String TYPE = "DeliveryCompleted";
}