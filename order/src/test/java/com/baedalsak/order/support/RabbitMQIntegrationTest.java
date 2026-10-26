package com.baedalsak.order.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * IntegrationTest + 실제 RabbitMQ(컨테이너). Outbox 발행기와 이벤트 리스너를 켠다.
 */
@TestPropertySource(properties = {
        "outbox.publisher.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
public abstract class RabbitMQIntegrationTest extends IntegrationTest {

    // 이 클래스를 상속한 테스트 전체가 컨테이너 1개를 공유한다
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.2");

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbit(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }
}
