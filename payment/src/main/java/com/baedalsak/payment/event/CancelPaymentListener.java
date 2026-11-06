package com.baedalsak.payment.event;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.baedalsak.payment.config.RabbitConfig;
import com.baedalsak.payment.service.PaymentCancelService;

import lombok.RequiredArgsConstructor;

/**
 * 결제 취소 명령 리스너 (#127). 메일 리스너와 분리한 컨테이너(cancelCommandContainerFactory)에서 한 건씩 처리한다.
 * messageId(=명령 ID)가 없거나 UUID 가 아니면 멱등 처리를 할 수 없으므로 바로 DLQ.
 */
@Component
@RequiredArgsConstructor
public class CancelPaymentListener {

  private final PaymentCancelService paymentCancelService;

  @RabbitListener(queues = RabbitConfig.CANCEL_COMMAND_QUEUE, containerFactory = RabbitConfig.CANCEL_COMMAND_CONTAINER_FACTORY)
  public void onCancelPayment(CancelPaymentCommand command,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    paymentCancelService.handle(parseMessageId(messageId), command);
  }

  private static UUID parseMessageId(String messageId) {
    if (messageId == null) {
      throw new AmqpRejectAndDontRequeueException(CancelPaymentCommand.TYPE + " without messageId");
    }
    try {
      return UUID.fromString(messageId);
    } catch (IllegalArgumentException e) {
      throw new AmqpRejectAndDontRequeueException(CancelPaymentCommand.TYPE + " with invalid messageId: " + messageId, e);
    }
  }
}
