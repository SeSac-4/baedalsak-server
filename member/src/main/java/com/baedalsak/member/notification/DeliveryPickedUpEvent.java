package com.baedalsak.member.notification;

/** delivery.picked_up (발행: delivery) 중 문자에 필요한 값만 */
public record DeliveryPickedUpEvent(Long orderId, Long customerId) {
  public static final String TYPE = "DeliveryPickedUp";
}