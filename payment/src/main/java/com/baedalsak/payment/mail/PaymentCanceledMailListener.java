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
import com.baedalsak.payment.domain.CancelRequester;
import com.baedalsak.payment.event.PaymentCanceledEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 환불 완료 메일 (#180, 설계서 기능명세서 7.1.1 · '이벤트 목록' PaymentCanceled). payment 가 발행한 PaymentCanceled(토스 전액 취소 성공)를
 * 자기 메일 큐로 받아 고객에게만 SES 로 보낸다. 환불 처리 중 · 확정 거절 · 결과 모름은 이 이벤트가 없어 보내지 않는다.
 * 규칙은 결제완료 메일(#37)과 같다: 동기 발송, mail_log 선점 → 발송 → SENT, 최대 3회 시도, 영구 오류 · eventId 오류는 DLQ.
 * 사유 한 줄만 requestedBy 로 다르게 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCanceledMailListener {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter CANCELED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final MailLogWriter mailLogWriter;
  private final MailSender mailSender;
  private final PaymentMailProperties properties;

  @RabbitListener(queues = RabbitConfig.PAYMENT_CANCELED_MAIL_QUEUE)
  public void onPaymentCanceled(PaymentCanceledEvent event,
      @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
    UUID eventId = parseEventId(messageId);
    String recipient = event.customerEmail();
    String templateName = properties.templates().canceledCustomer();
    if (!mailLogWriter.claim(eventId, recipient)) {
      log.info("이미 보낸 메일 건너뜀 eventId={}, template={}", eventId, templateName);
      return;
    }
    try {
      mailSender.send(recipient, templateName, templateData(event));
    } catch (PermanentMailFailureException e) {
      // 다시 보내도 실패 → 재시도 없이 DLQ (mail_log 는 SENDING 으로 남음, DLQ 에서 수동 확인)
      throw new AmqpRejectAndDontRequeueException("환불 완료 메일 영구 실패 eventId=" + eventId, e);
    }
    mailLogWriter.markSent(eventId, recipient);
  }

  // 템플릿 변수: 주문번호 · 가게 · 환불 금액 · 환불 시각 KST · 사유 한 줄
  private Map<String, String> templateData(PaymentCanceledEvent event) {
    return Map.of(
        "orderId", String.valueOf(event.orderId()),
        "storeName", event.storeName(),
        "amount", String.format("%,d원", event.cancelAmount()),
        "canceledAt", event.canceledAt().atZoneSameInstant(KST).format(CANCELED_AT_FORMAT),
        "reason", reason(event.requestedBy()));
  }

  // 설계서 기능명세서 7.1.1 환불 완료 사유 문구
  static String reason(String requestedBy) {
    return switch (CancelRequester.valueOf(requestedBy)) {
      case OWNER -> "가게 사정으로 주문이 거절되어";
      case CUSTOMER -> "주문을 취소하여";
      case SYSTEM -> "결제 확인이 늦어져 자동으로";
    };
  }

  // messageId(=eventId)가 없거나 UUID 가 아니면 멱등 처리를 할 수 없으므로 거부 → DLQ (docs 3-3)
  private UUID parseEventId(String messageId) {
    if (messageId == null) {
      throw new AmqpRejectAndDontRequeueException(PaymentCanceledEvent.TYPE + " without messageId");
    }
    try {
      return UUID.fromString(messageId);
    } catch (IllegalArgumentException e) {
      throw new AmqpRejectAndDontRequeueException(PaymentCanceledEvent.TYPE + " with invalid messageId: " + messageId, e);
    }
  }
}
