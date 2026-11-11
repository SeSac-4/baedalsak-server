package com.baedalsak.payment.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
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
  // 환불 완료 메일 큐 (#180). PaymentCanceled 를 order 와 따로 받는다
  public static final String PAYMENT_CANCELED_MAIL_QUEUE = "payment.mail.payment-canceled";
  // 결제 취소 명령 큐 (#127). 명령은 조정자(order)가 보내고 받는 payment 가 선언한다
  public static final String CANCEL_COMMAND_QUEUE = "payment.command.cancel";
  public static final String CANCEL_COMMAND_ROUTING_KEY = "command.payment.cancel";
  public static final String CANCEL_COMMAND_CONTAINER_FACTORY = "cancelCommandContainerFactory";

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

  // 환불 완료 메일 큐 + DLQ (#180). 결제완료 메일 큐와 같은 규칙
  @Bean
  Declarables paymentCanceledMailQueues() {
    Queue main = QueueBuilder.durable(PAYMENT_CANCELED_MAIL_QUEUE)
        .quorum()
        .deliveryLimit(3)
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(PAYMENT_CANCELED_MAIL_QUEUE)
        .build();
    Queue dlq = QueueBuilder.durable(PAYMENT_CANCELED_MAIL_QUEUE + ".dlq").quorum().build();
    return new Declarables(main, dlq,
        BindingBuilder.bind(main).to(new TopicExchange(EVENTS_EXCHANGE)).with("payment.canceled"),
        BindingBuilder.bind(dlq).to(new DirectExchange(DEAD_LETTER_EXCHANGE)).with(PAYMENT_CANCELED_MAIL_QUEUE));
  }

  // 결제 취소 명령 큐 + DLQ (#127, 둘 다 quorum). deliveryLimit 3 · DLX 는 메일 큐와 같음. DLQ 는 수동 확인 (재처리 API 대상)
  @Bean
  Declarables cancelCommandQueues() {
    Queue main = QueueBuilder.durable(CANCEL_COMMAND_QUEUE)
        .quorum()
        .deliveryLimit(3)
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(CANCEL_COMMAND_QUEUE)
        .build();
    Queue dlq = QueueBuilder.durable(CANCEL_COMMAND_QUEUE + ".dlq").quorum().build();
    return new Declarables(main, dlq,
        BindingBuilder.bind(main).to(new TopicExchange(EVENTS_EXCHANGE)).with(CANCEL_COMMAND_ROUTING_KEY),
        BindingBuilder.bind(dlq).to(new DirectExchange(DEAD_LETTER_EXCHANGE)).with(CANCEL_COMMAND_QUEUE));
  }

  // 메시지 본문 JSON (Jackson 3). 모르는 필드는 무시 → 발행 쪽이 필드를 추가해도 소비 쪽이 깨지지 않음
  @Bean
  MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  /**
   * 리스너 컨테이너 팩토리 (#126). Boot 가 만드는 것과 같게 설정(application.yml listener · retry, 아래 skipRetryOnReject)한 뒤,
   * advice chain 맨 앞(재시도 바깥)에 OriginTraceIdAdvice 를 넣는다. 이 팩토리를 쓰는 모든 @RabbitListener 에 적용된다.
   * 이후 별도 컨테이너(결제 취소 명령 리스너)를 만들 때도 withOriginTraceId 로 같은 설정을 적용한다.
   */
  @Bean(name = "rabbitListenerContainerFactory")
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
      ObjectProvider<ContainerCustomizer<SimpleMessageListenerContainer>> containerCustomizer) {
    return newContainerFactory(configurer, connectionFactory, containerCustomizer);
  }

  // @Bean 메서드끼리 서로 부르면 같은 빈을 돌려받으므로, 팩토리는 매번 이 메서드로 새로 만든다
  private static SimpleRabbitListenerContainerFactory newContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
      ObjectProvider<ContainerCustomizer<SimpleMessageListenerContainer>> containerCustomizer) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    containerCustomizer.ifUnique(factory::setContainerCustomizer);
    factory.setAdviceChain(withOriginTraceId(factory.getAdviceChain()));
    return factory;
  }

  /**
   * 결제 취소 명령 전용 컨테이너 팩토리 (#127). 메일 리스너와 컨테이너를 나눠, 토스 취소(최대 60초)가 메일 발송을 막지 않게 한다.
   * 설정은 메일과 같다(application.yml listener: prefetch 1 · 동시성 1 · 3회 시도 후 DLQ, 영구 오류는 바로 DLQ, originTraceId).
   * 팀 규칙의 업무 큐 값(prefetch 10)과 다른 이유: 환불은 한 결제씩 토스를 부르고, 파드가 죽어도 재전달을 1건으로 줄이기 위함.
   */
  @Bean(name = CANCEL_COMMAND_CONTAINER_FACTORY)
  SimpleRabbitListenerContainerFactory cancelCommandContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
      ObjectProvider<ContainerCustomizer<SimpleMessageListenerContainer>> containerCustomizer) {
    return newContainerFactory(configurer, connectionFactory, containerCustomizer);
  }

  // 기존 advice chain(재시도 등) 앞에 OriginTraceIdAdvice 를 붙인다. 앞에 둔 advice 가 바깥에서 감싼다
  static Advice[] withOriginTraceId(Advice[] chain) {
    Advice[] existing = chain == null ? new Advice[0] : chain;
    Advice[] result = new Advice[existing.length + 1];
    result[0] = new OriginTraceIdAdvice();
    System.arraycopy(existing, 0, result, 1, existing.length);
    return result;
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
