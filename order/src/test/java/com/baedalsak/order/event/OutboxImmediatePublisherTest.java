package com.baedalsak.order.event;

import com.baedalsak.order.config.RabbitConfig;
import com.baedalsak.order.support.RabbitMQIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 커밋 직후 즉시 발행. 폴링 주기를 1시간으로 늘려, 폴링이 아니라 커밋 신호로 나갔는지 확인한다 (실제 RabbitMQ).
 */
@TestPropertySource(properties = "outbox.publisher.poll-interval-ms=3600000")
class OutboxImmediatePublisherTest extends RabbitMQIntegrationTest {

    static final String TEST_QUEUE = "test.outbox-immediate";
    static final String ROUTING_KEY = "test.outbox-immediate";

    @Autowired
    OutboxAppender outboxAppender;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @BeforeEach
    void bindQueue() {
        Queue queue = new Queue(TEST_QUEUE, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE)).with(ROUTING_KEY));
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
    }

    @Test
    void 커밋되면_폴링을_기다리지_않고_발행한다() throws Exception {
        transactionTemplate.executeWithoutResult(status -> append(1L));

        assertThat(rabbitTemplate.receive(TEST_QUEUE, 5_000)).isNotNull();
        awaitAllPublished(1);
    }

    @Test
    void 롤백되면_발행하지_않는다() {
        transactionTemplate.executeWithoutResult(status -> {
            append(1L);
            status.setRollbackOnly();
        });

        assertThat(rabbitTemplate.receive(TEST_QUEUE, 2_000)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders.outbox", Integer.class)).isZero();
    }

    @Test
    void 커밋이_몰려도_빠지는_행_없이_모두_발행한다() throws Exception {
        int count = 30;
        for (long orderId = 1; orderId <= count; orderId++) {
            long id = orderId;
            transactionTemplate.executeWithoutResult(status -> append(id));
        }

        awaitAllPublished(count);
    }

    @Test
    void 트랜잭션_하나의_여러_행도_모두_발행한다() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            append(1L);
            append(2L);
        });

        awaitAllPublished(2);
    }

    private void append(long orderId) {
        outboxAppender.append("OutboxImmediateTest", ROUTING_KEY, orderId, Map.of("orderId", orderId));
    }

    private void awaitAllPublished(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (publishedCount() < expected && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(publishedCount()).isEqualTo(expected);
    }

    private int publishedCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.outbox WHERE published_at IS NOT NULL", Integer.class);
    }
}
