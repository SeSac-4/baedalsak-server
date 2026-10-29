package com.baedalsak.payment.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ exchange·큐·DLQ 선언 (docs 3-1·10-3, 설계서 '이벤트 목록' 탭).
 * payment 는 PaymentApproved 를 baedalsak.events 로 발행하고, 자기 메일 큐(payment.mail.payment-approved)로 다시 받아
 * 결제완료 메일을 보낸다. 업무 큐(order.payment-approved)는 받는 서비스(order)가 선언한다.
 * 이미 만들어진 큐의 인자(DLX·quorum·deliveryLimit)를 바꾸면 PRECONDITION_FAILED 로 앱이 시작되지 않으니 주의.
 */
@Configuration
public class RabbitConfig {

  public static final String EVENTS_EXCHANGE = "baedalsak.events";
  public static final String DEAD_LETTER_EXCHANGE = "payment.dlx";
  public static final String PAYMENT_APPROVED_MAIL_QUEUE = "payment.mail.payment-approved";

  // 전 서비스 공용 topic exchange. 이미 있으면 그대로 쓴다
  @Bean
  TopicExchange eventsExchange() {
    return new TopicExchange(EVENTS_EXCHANGE);
  }

  // 서비스당 DLX 1개 (direct). DLQ 라우팅 키 = 원래 큐 이름
  @Bean
  DirectExchange deadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE);
  }

  // 메일 큐 + DLQ (둘 다 quorum). 재전달은 deliveryLimit 3 까지, 포기한 메시지는 payment.dlx → <큐>.dlq. DLQ 에는 리스너를 붙이지 않는다
  @Bean
  Declarables paymentApprovedMailQueues() {
    Queue main = QueueBuilder.durable(PAYMENT_APPROVED_MAIL_QUEUE)
        .quorum()
        .deliveryLimit(3)
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(PAYMENT_APPROVED_MAIL_QUEUE)
        .build();
    Queue dlq = QueueBuilder.durable(PAYMENT_APPROVED_MAIL_QUEUE + ".dlq").quorum().build();
    return new Declarables(main, dlq,
        BindingBuilder.bind(main).to(new TopicExchange(EVENTS_EXCHANGE)).with("payment.approved"),
        BindingBuilder.bind(dlq).to(new DirectExchange(DEAD_LETTER_EXCHANGE)).with(PAYMENT_APPROVED_MAIL_QUEUE));
  }

  // 메시지 본문 JSON (Jackson 3). 모르는 필드는 무시 → 발행 쪽이 필드를 추가해도 소비 쪽이 깨지지 않음
  @Bean
  MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  // 영구 오류(AmqpRejectAndDontRequeueException)는 앱 안에서 재시도하지 않고 바로 DLQ (docs 10-4)
  @Bean
  RabbitListenerRetrySettingsCustomizer skipRetryOnReject() {
    return settings -> settings.setExceptionPredicate(RabbitConfig::isRetryable);
  }

  static boolean isRetryable(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof AmqpRejectAndDontRequeueException) {
        return false;
      }
    }
    return true;
  }
}
