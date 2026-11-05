package com.baedalsak.order.config;

import com.baedalsak.order.support.RabbitMQIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 취소 응답 큐. 리스너를 붙이기 전이라 payment 가 보낸 메시지는 큐에 남아 있어야 한다.
 */
class PaymentCancelQueueTest extends RabbitMQIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MILLIS = 5000;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Test
    void PaymentCanceled_는_응답_큐에_쌓인다() {
        assertReceived("payment.canceled", RabbitConfig.PAYMENT_CANCELED_QUEUE);
    }

    @Test
    void PaymentCancelFailed_는_응답_큐에_쌓인다() {
        assertReceived("payment.cancel-failed", RabbitConfig.PAYMENT_CANCEL_FAILED_QUEUE);
    }

    private void assertReceived(String routingKey, String queue) {
        rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, routingKey, "{\"orderId\":1}");

        Message received = rabbitTemplate.receive(queue, RECEIVE_TIMEOUT_MILLIS);

        assertThat(received).isNotNull();
    }
}
