package com.baedalsak.order.event;

import com.baedalsak.order.config.RabbitConfig;
import com.baedalsak.order.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox 발행기 (#31). 실제 RabbitMQ(컨테이너)에 1초 폴링으로 발행되는지, 발행 확인을 받은 행만 published_at 이 찍히는지 확인한다.
 * 받는 쪽 큐(delivery.order-accepted 역할)는 테스트가 직접 선언한다.
 */
@TestPropertySource(properties = "outbox.publisher.enabled=true")
class OutboxPublisherTest extends IntegrationTest {

    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.2");
    static final String TEST_QUEUE = "test.order-accepted";

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

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @BeforeEach
    void bindQueue() {
        Queue queue = new Queue(TEST_QUEUE, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE)).with(OrderAcceptedPayload.ROUTING_KEY));
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
    }

    @Test
    void 미발행_행을_헤더와_함께_발행하고_published_at_을_기록한다() throws Exception {
        UUID eventId = insertOutbox(OrderAcceptedPayload.ROUTING_KEY, "abc123");

        Message message = rabbitTemplate.receive(TEST_QUEUE, 10_000);

        assertThat(message).isNotNull();
        var properties = message.getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(eventId.toString());
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat((String) properties.getHeader("eventType")).isEqualTo("OrderAccepted");
        assertThat((Integer) properties.getHeader("eventVersion")).isEqualTo(1);
        assertThat((String) properties.getHeader("occurredAt")).endsWith("+09:00");
        assertThat((String) properties.getHeader("traceId")).isEqualTo("abc123");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"orderId\": 1").contains("\"deliveryDong\": \"연남동\"");

        awaitPublished(eventId);
    }

    @Test
    void 한_번_발행한_행은_다시_보내지_않는다() throws Exception {
        UUID eventId = insertOutbox(OrderAcceptedPayload.ROUTING_KEY, null);
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 10_000)).isNotNull();
        awaitPublished(eventId);

        // 폴링이 몇 번 더 돌아도 새 메시지가 없다
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 3_000)).isNull();
    }

    @Test
    void 받을_큐가_없으면_return_이라_published_at_을_남기지_않는다() throws Exception {
        UUID eventId = insertOutbox("order.unbound", null);

        // 폴링이 여러 번 돌 동안 기다린다
        Thread.sleep(3_000);

        assertThat(publishedAt(eventId)).isNull();
    }

    private UUID insertOutbox(String routingKey, String traceId) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders.outbox (event_id, event_type, routing_key, aggregate_id, payload, trace_id)
                VALUES (?, 'OrderAccepted', ?, 1, '{"orderId":1,"deliveryDong":"연남동"}'::jsonb, ?)
                """, eventId, routingKey, traceId);
        return eventId;
    }

    private void awaitPublished(UUID eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (publishedAt(eventId) == null && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(publishedAt(eventId)).isNotNull();
    }

    private Object publishedAt(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT published_at FROM orders.outbox WHERE event_id = ?", Object.class, eventId);
    }
}
