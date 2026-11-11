package com.baedalsak.member.config;

import java.time.Duration;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

import com.baedalsak.common.messaging.ListenerErrorHandler;
import com.baedalsak.common.messaging.ListenerFailureRecoverer;

/**
 * 주문·배달 알림 문자 큐 (#182). member 는 이벤트를 발행하지 않고 구독만 한다.
 * 큐는 받는 서비스가 선언·소유한다 (docs 3-1) → member.sms.* 와 DLQ 를 member 가 선언.
 * 규칙은 메일 큐와 같다 (docs 10-3·10-4): quorum · deliveryLimit 3 · DLX member.dlx → <큐>.dlq
 * 이미 만들어진 큐의 인자(DLX·quorum 등)를 바꾸면 PRECONDITION_FAILED 로 앱이 시작되지 않으니 주의.
 */
@Configuration
public class RabbitConfig {

  public static final String EVENTS_EXCHANGE = "baedalsak.events";   // 팀 공통 exchange
  public static final String DEAD_LETTER_EXCHANGE = "member.dlx";

  // 구독하는 이벤트 (발행: order · delivery). 이름이 바뀌면 문자가 안 감 → 팀에 공지
  public static final String ORDER_ACCEPTED_ROUTING_KEY = "order.accepted";
  public static final String DELIVERY_PICKED_UP_ROUTING_KEY = "delivery.picked_up";
  public static final String DELIVERY_COMPLETED_ROUTING_KEY = "delivery.completed";

  public static final String ORDER_ACCEPTED_SMS_QUEUE = "member.sms.order-accepted";
  public static final String DELIVERY_PICKED_UP_SMS_QUEUE = "member.sms.delivery-picked-up";
  public static final String DELIVERY_COMPLETED_SMS_QUEUE = "member.sms.delivery-completed";

  // 문자 리스너 전용 설정 이름 (@RabbitListener(containerFactory = ...) 에서 씀)
  public static final String SMS_LISTENER_FACTORY = "smsListenerContainerFactory";

  @Bean
  Declarables orderAcceptedSmsQueues() {
    return smsQueues(ORDER_ACCEPTED_SMS_QUEUE, ORDER_ACCEPTED_ROUTING_KEY);
  }

  @Bean
  Declarables deliveryPickedUpSmsQueues() {
    return smsQueues(DELIVERY_PICKED_UP_SMS_QUEUE, DELIVERY_PICKED_UP_ROUTING_KEY);
  }

  @Bean
  Declarables deliveryCompletedSmsQueues() {
    return smsQueues(DELIVERY_COMPLETED_SMS_QUEUE, DELIVERY_COMPLETED_ROUTING_KEY);
  }

  // 문자 큐 + DLQ (둘 다 quorum). 재전달은 deliveryLimit 3 까지, 포기한 메시지는 member.dlx → <큐>.dlq. DLQ 에는 리스너를 붙이지 않는다
  private static Declarables smsQueues(String queueName, String routingKey) {
    TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
    DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE);
    Queue main = QueueBuilder.durable(queueName)
        .quorum()
        .deliveryLimit(3)
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(queueName)
        .build();
    Queue dlq = QueueBuilder.durable(queueName + ".dlq").quorum().build();
    return new Declarables(events, deadLetterExchange, main, dlq,
        BindingBuilder.bind(main).to(events).with(routingKey),
        BindingBuilder.bind(dlq).to(deadLetterExchange).with(queueName));
  }

  /**
   * 문자 리스너 규칙 (메일 리스너와 같음, docs 10-4): 동시성 1 · prefetch 1 ·
   * 최대 3회 시도(최초 1회 + 재시도 2회, 2초 ×2) 후 DLQ, 영구 오류(AmqpRejectAndDontRequeueException)는 바로 DLQ.
   * 나머지(traceId · requeue 금지)는 application.yml 의 listener.simple 설정을 그대로 받는다.
   */
  @Bean(SMS_LISTENER_FACTORY)
  SimpleRabbitListenerContainerFactory smsListenerContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(1);
    factory.setPrefetchCount(1);
    factory.setAdviceChain(smsRetryInterceptor());
    factory.setErrorHandler(new ListenerErrorHandler());
    return factory;
  }

  static MethodInterceptor smsRetryInterceptor() {
    return RetryInterceptorBuilder.stateless()
        .retryPolicy(smsRetryPolicy())
        .recoverer(new ListenerFailureRecoverer())   // 다 실패하면 requeue 하지 않고 거부 → DLQ
        .build();
  }

  static RetryPolicy smsRetryPolicy() {
    return RetryPolicy.builder()
        .maxRetries(2)
        .delay(Duration.ofSeconds(2))
        .multiplier(2)
        .maxDelay(Duration.ofSeconds(10))
        .predicate(RabbitConfig::isRetryable)
        .build();
  }

  // 영구 오류(이벤트 ID 없음 · JSON 깨짐)는 재시도하지 않는다. 판단은 common 처리기와 같은 규칙 (#186)
  static boolean isRetryable(Throwable error) {
    return !ListenerFailureRecoverer.isPermanent(error);
  }
  
  // 메시지 본문 JSON (Jackson 3). 모르는 필드는 무시 → 발행 쪽이 필드를 추가해도 member 는 깨지지 않음
  @Bean
  MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }
}