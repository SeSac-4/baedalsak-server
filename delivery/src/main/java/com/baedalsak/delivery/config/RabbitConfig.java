package com.baedalsak.delivery.config;

import com.baedalsak.delivery.payload.DeliveryAssignedEvent;
import com.baedalsak.delivery.payload.DeliveryPickedUpEvent;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

import java.time.Duration;

/**
 * RabbitMQ exchange·큐·DLQ 선언 (docs 3-1, 설계서 '이벤트 목록' 탭).
 * 큐는 받는 서비스가 선언·소유한다 → delivery 는 delivery.order-accepted 를 선언.
 * 메일 큐(delivery.mail.*)는 delivery 가 자기가 발행한 이벤트를 다시 받는 큐라 delivery 가 선언한다 (docs 10-3).
 * 이미 만들어진 큐의 인자(DLX·quorum 등)를 바꾸면 PRECONDITION_FAILED 로 앱이 시작되지 않으니 주의.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "baedalsak.events";
    public static final String DEAD_LETTER_EXCHANGE = "delivery.dlx";

    public static final String ORDER_ACCEPTED_QUEUE = "delivery.order-accepted";
    public static final String ORDER_ACCEPTED_ROUTING_KEY = "order.accepted";

    public static final String DELIVERY_ASSIGNED_MAIL_QUEUE = "delivery.mail.delivery-assigned";
    public static final String DELIVERY_PICKED_UP_MAIL_QUEUE = "delivery.mail.delivery-picked-up";

    // 메일 리스너 전용 컨테이너 팩토리. 업무 리스너(OrderAccepted)는 Boot 기본 팩토리(prefetch 10)를 그대로 쓴다
    public static final String MAIL_LISTENER_FACTORY = "mailListenerContainerFactory";

    // 업무 큐 + DLQ (둘 다 quorum). retry 3회 후 포기한 메시지는 delivery.dlx → <큐>.dlq
    @Bean
    Declarables orderAcceptedQueues() {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE);
        Queue main = QueueBuilder.durable(ORDER_ACCEPTED_QUEUE)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ORDER_ACCEPTED_QUEUE)
                .build();
        Queue dlq = QueueBuilder.durable(ORDER_ACCEPTED_QUEUE + ".dlq").quorum().build();
        return new Declarables(events, deadLetterExchange, main, dlq,
                BindingBuilder.bind(main).to(events).with(ORDER_ACCEPTED_ROUTING_KEY),
                BindingBuilder.bind(dlq).to(deadLetterExchange).with(ORDER_ACCEPTED_QUEUE));
    }

    @Bean
    Declarables deliveryAssignedMailQueues() {
        return mailQueues(DELIVERY_ASSIGNED_MAIL_QUEUE, DeliveryAssignedEvent.ROUTING_KEY);
    }

    @Bean
    Declarables deliveryPickedUpMailQueues() {
        return mailQueues(DELIVERY_PICKED_UP_MAIL_QUEUE, DeliveryPickedUpEvent.ROUTING_KEY);
    }

    // 메일 큐 + DLQ (둘 다 quorum). 재전달은 deliveryLimit 3 까지, 포기한 메시지는 delivery.dlx → <큐>.dlq. DLQ 에는 리스너를 붙이지 않는다
    private static Declarables mailQueues(String queueName, String routingKey) {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE);
        Queue main = QueueBuilder.durable(queueName)
                .quorum()
                .deliveryLimit(3)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(queueName)
                .build();
        Queue dlq = QueueBuilder.durable(queueName + ".dlq").quorum().build();
        return new Declarables(main, dlq,
                BindingBuilder.bind(main).to(events).with(routingKey),
                BindingBuilder.bind(dlq).to(deadLetterExchange).with(queueName));
    }

    /**
     * 메일 리스너 규칙 (docs 10-4, 설계서 '이벤트 목록' 메일 리스너): 동시성 1 · prefetch 1 ·
     * 최대 3회 시도(최초 1회 + 재시도 2회, 2초 ×2) 후 DLQ, 영구 오류(AmqpRejectAndDontRequeueException)는 바로 DLQ.
     * 나머지(JSON 변환·traceId·requeue 금지)는 application.yml 의 listener.simple 설정을 그대로 받는다.
     */
    @Bean(MAIL_LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory mailListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setAdviceChain(mailRetryInterceptor());
        return factory;
    }

    static MethodInterceptor mailRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(mailRetryPolicy())
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    static RetryPolicy mailRetryPolicy() {
        return RetryPolicy.builder()
                .maxRetries(2)
                .delay(Duration.ofSeconds(2))
                .multiplier(2)
                .maxDelay(Duration.ofSeconds(10))
                .predicate(RabbitConfig::isRetryable)
                .build();
    }

    static boolean isRetryable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof AmqpRejectAndDontRequeueException) {
                return false;
            }
        }
        return true;
    }

    // 메시지 본문 JSON (Jackson 3). 모르는 필드는 무시 → 발행 쪽이 필드를 추가해도 소비 쪽이 깨지지 않음
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
