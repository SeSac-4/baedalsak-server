package com.baedalsak.member.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.baedalsak.member.config.RabbitConfig;

import lombok.RequiredArgsConstructor;

/**
 * 알림 문자 리스너 3개 (#182). 큐에 메시지가 오면 실행된다. 동기 발송 — 메서드가 끝나야 ack (큐에서 지움).
 */
@Component
@RequiredArgsConstructor
public class SmsNotificationListener {

  private final SmsNotificationService smsNotificationService;

  @RabbitListener(queues = RabbitConfig.ORDER_ACCEPTED_SMS_QUEUE, containerFactory = RabbitConfig.SMS_LISTENER_FACTORY)
  public void onOrderAccepted(OrderAcceptedEvent event,
                              @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    smsNotificationService.send(
        SmsNotificationService.parseEventId(OrderAcceptedEvent.TYPE, messageId),
        OrderAcceptedEvent.TYPE, event.customerId(),
        "[배달싹] " + event.storeName() + " 주문이 접수되어 조리를 시작했어요");
  }

  @RabbitListener(queues = RabbitConfig.DELIVERY_PICKED_UP_SMS_QUEUE, containerFactory = RabbitConfig.SMS_LISTENER_FACTORY)
  public void onDeliveryPickedUp(DeliveryPickedUpEvent event,
                                 @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    smsNotificationService.send(
        SmsNotificationService.parseEventId(DeliveryPickedUpEvent.TYPE, messageId),
        DeliveryPickedUpEvent.TYPE, event.customerId(),
        "[배달싹] 라이더가 음식을 픽업해 배달을 시작했어요");
  }

  @RabbitListener(queues = RabbitConfig.DELIVERY_COMPLETED_SMS_QUEUE, containerFactory = RabbitConfig.SMS_LISTENER_FACTORY)
  public void onDeliveryCompleted(DeliveryCompletedEvent event,
                                  @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    smsNotificationService.send(
        SmsNotificationService.parseEventId(DeliveryCompletedEvent.TYPE, messageId),
        DeliveryCompletedEvent.TYPE, event.customerId(),
        "[배달싹] 배달이 완료됐어요. 맛있게 드세요!");
  }
}