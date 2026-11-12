package com.baedalsak.order.config;

import com.baedalsak.order.event.OrderAcceptedPayload;
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
 * 큐는 받는 서비스가 선언·소유한다 → order 는 payment·delivery 이벤트 큐 6개를 선언.
 * 메일 큐(order.mail.order-accepted)는 order 가 자기가 발행한 OrderAccepted 를 다시 받는 큐라 order 가 선언한다 (#33, docs 10-3).
 * 이미 만들어진 큐의 인자(DLX·quorum 등)를 바꾸면 PRECONDITION_FAILED 로 앱이 시작되지 않으니 주의.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "baedalsak.events";
    public static final String DEAD_LETTER_EXCHANGE = "order.dlx";

    public static final String PAYMENT_APPROVED_QUEUE = "order.payment-approved";
    public static final String PAYMENT_CANCELED_QUEUE = "order.payment-canceled";
    public static final String PAYMENT_CANCEL_FAILED_QUEUE = "order.payment-cancel-failed";
    public static final String DELIVERY_ASSIGNED_QUEUE = "order.delivery-assigned";
    public static final String DELIVERY_PICKED_UP_QUEUE = "order.delivery-picked-up";
    public static final String DELIVERY_COMPLETED_QUEUE = "order.delivery-completed";

    public static final String ORDER_ACCEPTED_MAIL_QUEUE = "order.mail.order-accepted";

    // 메일 리스너 전용 컨테이너 팩토리. 업무 리스너(결제 · 배달 이벤트)는 Boot 기본 팩토리(prefetch 10)를 그대로 쓴다
    public static final String MAIL_LISTENER_FACTORY = "mailListenerContainerFactory";

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

    @Bean
    Declarables paymentApprovedQueues() {
        return consumerQueues(PAYMENT_APPROVED_QUEUE, "payment.approved");
    }

    // 결제 취소 응답 큐. payment 발행기가 mandatory 라 #127 배포 전에 있어야 한다. 소비 리스너는 거절·취소 작업에서 붙인다
    @Bean
    Declarables paymentCanceledQueues() {
        return consumerQueues(PAYMENT_CANCELED_QUEUE, "payment.canceled");
    }

    @Bean
    Declarables paymentCancelFailedQueues() {
        return consumerQueues(PAYMENT_CANCEL_FAILED_QUEUE, "payment.cancel-failed");
    }

    @Bean
    Declarables deliveryAssignedQueues() {
        return consumerQueues(DELIVERY_ASSIGNED_QUEUE, "delivery.assigned");
    }

    @Bean
    Declarables deliveryPickedUpQueues() {
        return consumerQueues(DELIVERY_PICKED_UP_QUEUE, "delivery.picked_up");
    }

    @Bean
    Declarables deliveryCompletedQueues() {
        return consumerQueues(DELIVERY_COMPLETED_QUEUE, "delivery.completed");
    }

    // 조리 시작 메일 큐 (#33). 고객에게 메일을 보내는 자기 메일 큐
    @Bean
    Declarables orderAcceptedMailQueues() {
        return mailQueues(ORDER_ACCEPTED_MAIL_QUEUE, OrderAcceptedPayload.ROUTING_KEY);
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

    // 업무 큐 + DLQ (둘 다 quorum). retry 3회 후 포기한 메시지는 order.dlx → <큐>.dlq. DLQ 에는 리스너를 붙이지 않는다
    private static Declarables consumerQueues(String queue, String routingKey) {
        Queue main = QueueBuilder.durable(queue)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(queue)
                .build();
        Queue dlq = QueueBuilder.durable(queue + ".dlq").quorum().build();
        return new Declarables(main, dlq,
                BindingBuilder.bind(main).to(new TopicExchange(EVENTS_EXCHANGE)).with(routingKey),
                BindingBuilder.bind(dlq).to(new DirectExchange(DEAD_LETTER_EXCHANGE)).with(queue));
    }

    // 메일 큐 + DLQ (둘 다 quorum). 재전달은 deliveryLimit 3 까지, 포기한 메시지는 order.dlx → <큐>.dlq. DLQ 에는 리스너를 붙이지 않는다
    private static Declarables mailQueues(String queue, String routingKey) {
        Queue main = QueueBuilder.durable(queue)
                .quorum()
                .deliveryLimit(3)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(queue)
                .build();
        Queue dlq = QueueBuilder.durable(queue + ".dlq").quorum().build();
        return new Declarables(main, dlq,
                BindingBuilder.bind(main).to(new TopicExchange(EVENTS_EXCHANGE)).with(routingKey),
                BindingBuilder.bind(dlq).to(new DirectExchange(DEAD_LETTER_EXCHANGE)).with(queue));
    }
}
