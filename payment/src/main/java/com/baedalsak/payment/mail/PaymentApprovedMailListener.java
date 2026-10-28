package com.baedalsak.payment.mail;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.baedalsak.payment.config.RabbitConfig;
import com.baedalsak.payment.event.PaymentApprovedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제완료 메일 (#37, docs 10번). payment 가 발행한 PaymentApproved 를 자기 메일 큐로 받아 고객·사장님에게 SES 로 보낸다.
 * 동기 발송(@Async 미사용) — 메서드가 끝나야 ack. 수신자별로 mail_log 선점 → 발송 → SENT.
 * 재시도(최대 3회 시도) 때는 SENT 인 수신자를 건너뛰고 SENDING 인 수신자만 다시 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApprovedMailListener {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter APPROVED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final MailLogWriter mailLogWriter;
  private final MailSender mailSender;
  private final PaymentMailProperties properties;

  @RabbitListener(queues = RabbitConfig.PAYMENT_APPROVED_MAIL_QUEUE)
  public void onPaymentApproved(PaymentApprovedEvent event,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    UUID eventId = parseEventId(messageId);
    Map<String, String> data = templateData(event);
    send(eventId, event.customerEmail(), properties.templates().customer(), data);
    send(eventId, event.ownerEmail(), properties.templates().owner(), data);
  }

  private void send(UUID eventId, String recipient, String templateName, Map<String, String> data) {
    if (!mailLogWriter.claim(eventId, recipient)) {
      log.info("이미 보낸 메일 건너뜀 eventId={}, template={}", eventId, templateName);
      return;
    }
    try {
      mailSender.send(recipient, templateName, data);
    } catch (PermanentMailFailureException e) {
      // 다시 보내도 실패 → 재시도 없이 DLQ (mail_log 는 SENDING 으로 남음, DLQ 에서 수동 확인)
      throw new AmqpRejectAndDontRequeueException("결제완료 메일 영구 실패 eventId=" + eventId, e);
    }
    mailLogWriter.markSent(eventId, recipient);
  }

  // 템플릿 변수: 결제완료 메일 초안 기준 (주문번호·가게·결제 금액·승인 시각 KST)
  private Map<String, String> templateData(PaymentApprovedEvent event) {
    return Map.of(
        "orderId", String.valueOf(event.orderId()),
        "storeName", event.storeName(),
        "amount", String.format("%,d원", event.amount()),
        "approvedAt", event.approvedAt().atZoneSameInstant(KST).format(APPROVED_AT_FORMAT));
  }

  // messageId(=eventId)가 없거나 UUID 가 아니면 멱등 처리를 할 수 없으므로 거부 → DLQ (docs 3-3)
  private UUID parseEventId(String messageId) {
    if (messageId == null) {
      throw new AmqpRejectAndDontRequeueException(PaymentApprovedEvent.TYPE + " without messageId");
    }
    try {
      return UUID.fromString(messageId);
    } catch (IllegalArgumentException e) {
      throw new AmqpRejectAndDontRequeueException(PaymentApprovedEvent.TYPE + " with invalid messageId: " + messageId, e);
    }
  }
}
