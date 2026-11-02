package com.baedalsak.delivery.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 메일 큐·메일 리스너 재시도 (#108, 설계서 '이벤트 목록' 메일 큐·DLQ). 큐 인자·바인딩과 재시도 조건
 */
class RabbitConfigTest {

    final RabbitConfig rabbitConfig = new RabbitConfig();

    @Test
    void 배차완료_메일_큐는_quorum_이고_delivery_dlx_로_보낸다() {
        assertMailQueue(rabbitConfig.deliveryAssignedMailQueues(), "delivery.mail.delivery-assigned", "delivery.assigned");
    }

    @Test
    void 배달시작_메일_큐는_quorum_이고_delivery_dlx_로_보낸다() {
        assertMailQueue(rabbitConfig.deliveryPickedUpMailQueues(), "delivery.mail.delivery-picked-up", "delivery.picked_up");
    }

    @Test
    void 배달완료_메일_큐는_quorum_이고_delivery_dlx_로_보낸다() {
        assertMailQueue(rabbitConfig.deliveryCompletedMailQueues(), "delivery.mail.delivery-completed", "delivery.completed");
    }

    @Test
    void 메일_리스너는_동시성_1_prefetch_1_이고_업무_큐와_다른_재시도를_쓴다() {
        SimpleRabbitListenerContainerFactory factory = rabbitConfig.mailListenerContainerFactory(
                new SimpleRabbitListenerContainerFactoryConfigurer(new RabbitProperties()), mock(ConnectionFactory.class));

        SimpleMessageListenerContainer container = factory.createListenerContainer();
        assertThat(ReflectionTestUtils.getField(container, "concurrentConsumers")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(container, "maxConcurrentConsumers")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(container, "prefetchCount")).isEqualTo(1);
        assertThat(factory.getAdviceChain()).hasSize(1);
    }

    @Test
    void 메일_재시도는_최초_1회_더하기_2회_2초_간격() {
        RetryPolicy policy = RabbitConfig.mailRetryPolicy();

        assertThat(policy.getBackOff().start().nextBackOff()).isEqualTo(Duration.ofSeconds(2).toMillis());
        assertThat(policy.shouldRetry(new IllegalStateException("throttled"))).isTrue();
    }

    @Test
    void 거부_예외가_원인에_있으면_재시도하지_않는다() {
        Throwable wrapped = new ListenerExecutionFailedException("listener failed",
                new AmqpRejectAndDontRequeueException("permanent"));

        assertThat(RabbitConfig.isRetryable(wrapped)).isFalse();
        assertThat(RabbitConfig.mailRetryPolicy().shouldRetry(wrapped)).isFalse();
    }

    @Test
    void 그_외_예외는_재시도한다() {
        assertThat(RabbitConfig.isRetryable(new IllegalStateException("throttled"))).isTrue();
    }

    private static void assertMailQueue(Declarables declarables, String queueName, String routingKey) {
        assertThat(queue(declarables, queueName).getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-delivery-limit", 3)
                .containsEntry("x-dead-letter-exchange", "delivery.dlx")
                .containsEntry("x-dead-letter-routing-key", queueName);
        assertThat(queue(declarables, queueName + ".dlq").getArguments())
                .containsEntry("x-queue-type", "quorum");
        assertThat(declarables.getDeclarablesByType(Binding.class))
                .anySatisfy(b -> {
                    assertThat(b.getExchange()).isEqualTo("baedalsak.events");
                    assertThat(b.getRoutingKey()).isEqualTo(routingKey);
                    assertThat(b.getDestination()).isEqualTo(queueName);
                })
                .anySatisfy(b -> {
                    assertThat(b.getExchange()).isEqualTo("delivery.dlx");
                    assertThat(b.getRoutingKey()).isEqualTo(queueName);
                    assertThat(b.getDestination()).isEqualTo(queueName + ".dlq");
                });
    }

    static Queue queue(Declarables declarables, String name) {
        return declarables.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
