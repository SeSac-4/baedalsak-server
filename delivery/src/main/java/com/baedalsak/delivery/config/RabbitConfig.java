package com.baedalsak.delivery.config;

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
 * 큐는 받는 서비스가 선언·소유한다 → delivery 는 delivery.order-accepted 를 선언.
 * 이미 만들어진 큐의 인자(DLX·quorum 등)를 바꾸면 PRECONDITION_FAILED 로 앱이 시작되지 않으니 주의.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "baedalsak.events";
    public static final String DEAD_LETTER_EXCHANGE = "delivery.dlx";

    public static final String ORDER_ACCEPTED_QUEUE = "delivery.order-accepted";
    public static final String ORDER_ACCEPTED_ROUTING_KEY = "order.accepted";

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

    // 메시지 본문 JSON (Jackson 3). 모르는 필드는 무시 → 발행 쪽이 필드를 추가해도 소비 쪽이 깨지지 않음
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
