package com.baedalsak.order.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 설정 (docs 3-1). order 가 받는 큐(payment·delivery 이벤트, 메일 큐)는 #32·#33 에서 여기에 선언한다.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "baedalsak.events";

    // 전 서비스 공용 topic exchange. 이미 있으면 그대로 쓴다
    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE);
    }

    // 메시지 본문 JSON (Jackson 3). 모르는 필드는 무시 → 발행 쪽이 필드를 추가해도 소비 쪽이 깨지지 않음
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
