package com.baedalsak.payment.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ exchange 선언 (docs 3-1, 설계서 '이벤트 목록' 탭).
 * payment 는 PaymentApproved 를 baedalsak.events 로 발행한다. 업무 큐는 받는 서비스(order)가 선언하고,
 * payment 메일 큐(payment.mail.payment-approved)는 메일 리스너와 함께 #37 에서 추가한다.
 */
@Configuration
public class RabbitConfig {

  public static final String EVENTS_EXCHANGE = "baedalsak.events";

  // 전 서비스 공용 topic exchange. 이미 있으면 그대로 쓴다
  @Bean
  TopicExchange eventsExchange() {
    return new TopicExchange(EVENTS_EXCHANGE);
  }
}
