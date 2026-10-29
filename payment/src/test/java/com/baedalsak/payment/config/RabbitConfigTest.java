package com.baedalsak.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제완료 메일 큐 (#37, 설계서 '이벤트 목록' 메일 큐·DLQ). 큐 인자·바인딩과 재시도 제외 조건
 */
class RabbitConfigTest {

    final RabbitConfig rabbitConfig = new RabbitConfig();

    @Test
    void 메일_큐는_quorum_이고_실패하면_payment_dlx_로_보낸다() {
        Declarables declarables = rabbitConfig.paymentApprovedMailQueues();

        Queue main = queue(declarables, "payment.mail.payment-approved");
        assertThat(main.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-dead-letter-exchange", "payment.dlx")
                .containsEntry("x-dead-letter-routing-key", "payment.mail.payment-approved");
        assertThat(queue(declarables, "payment.mail.payment-approved.dlq").getArguments())
                .containsEntry("x-queue-type", "quorum");
    }

    @Test
    void 메일_큐는_payment_approved_를_받고_DLQ_는_dlx_에_묶인다() {
        Declarables declarables = rabbitConfig.paymentApprovedMailQueues();

        assertThat(declarables.getDeclarablesByType(Binding.class))
                .anySatisfy(b -> {
                    assertThat(b.getExchange()).isEqualTo("baedalsak.events");
                    assertThat(b.getRoutingKey()).isEqualTo("payment.approved");
                    assertThat(b.getDestination()).isEqualTo("payment.mail.payment-approved");
                })
                .anySatisfy(b -> {
                    assertThat(b.getExchange()).isEqualTo("payment.dlx");
                    assertThat(b.getDestination()).isEqualTo("payment.mail.payment-approved.dlq");
                });
    }

    @Test
    void 거부_예외가_원인에_있으면_재시도하지_않는다() {
        Throwable wrapped = new ListenerExecutionFailedException("listener failed",
                new AmqpRejectAndDontRequeueException("permanent"));

        assertThat(RabbitConfig.isRetryable(wrapped)).isFalse();
    }

    @Test
    void 그_외_예외는_재시도한다() {
        assertThat(RabbitConfig.isRetryable(new IllegalStateException("throttled"))).isTrue();
    }

    private static Queue queue(Declarables declarables, String name) {
        return declarables.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
