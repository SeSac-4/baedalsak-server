package com.baedalsak.member.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.baedalsak.member.config.RabbitConfig;

import lombok.RequiredArgsConstructor;

/**
 * 알림 문자 리스너 (#182 고객 3개 · #217 사장님 1개). 큐에 메시지가 오면 실행된다. 동기 발송 — 메서드가 끝나야 ack (큐에서 지움).
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

  // 사장님 새 주문 (#217). 결제가 끝나야 사장님이 수락할 수 있는 주문이 되므로 결제 완료 이벤트를 쓴다
  @RabbitListener(queues = RabbitConfig.PAYMENT_APPROVED_SMS_QUEUE, containerFactory = RabbitConfig.SMS_LISTENER_FACTORY)
  public void onPaymentApproved(PaymentApprovedEvent event,
                                @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    smsNotificationService.send(
        SmsNotificationService.parseEventId(PaymentApprovedEvent.TYPE, messageId),
        PaymentApprovedEvent.TYPE, event.ownerId(),
        ownerNewOrderMessage(event));
  }

  // [배달싹] 시드분식 새 주문이 들어왔어요 (18,000원). 앱에서 주문을 수락해 주세요
  static String ownerNewOrderMessage(PaymentApprovedEvent event) {
    String amount = event.amount() == null ? "" : String.format(" (%,d원)", event.amount());
    return "[배달싹] " + event.storeName() + " 새 주문이 들어왔어요" + amount + ". 앱에서 주문을 수락해 주세요";
  }
}
