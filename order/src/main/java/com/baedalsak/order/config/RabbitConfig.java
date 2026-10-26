package com.baedalsak.order.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ exchange·큐·DLQ 선언 (docs 3-1, 설계서 '이벤트 목록' 탭).
 * 큐는 받는 서비스가 선언·소유한다 → order 는 payment·delivery 이벤트 큐 4개를 선언. 메일 큐는 #33 에서 추가.
 * 이미 만들어진 큐의 인자(DLX·quorum 등)를 바꾸면 PRECONDITION_FAILED 로 앱이 시작되지 않으니 주의.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "baedalsak.events";
    public static final String DEAD_LETTER_EXCHANGE = "order.dlx";

    public static final String PAYMENT_APPROVED_QUEUE = "order.payment-approved";
    public static final String DELIVERY_ASSIGNED_QUEUE = "order.delivery-assigned";
    public static final String DELIVERY_PICKED_UP_QUEUE = "order.delivery-picked-up";
    public static final String DELIVERY_COMPLETED_QUEUE = "order.delivery-completed";

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
}
