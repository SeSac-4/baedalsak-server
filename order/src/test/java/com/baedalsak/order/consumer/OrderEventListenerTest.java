package com.baedalsak.order.consumer;

import com.baedalsak.order.config.RabbitConfig;
import com.baedalsak.order.support.RabbitMQIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * payment·delivery 이벤트 소비 (#32). 실제 RabbitMQ 로 이벤트를 보내고 주문 상태가 바뀌는지 확인한다.
 * 주문 1번: 고객 7, 사장님 10, 총액 16500
 */
class OrderEventListenerTest extends RabbitMQIntegrationTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @BeforeEach
    void purgeDeadLetters() {
        amqpAdmin.purgeQueue(RabbitConfig.DELIVERY_PICKED_UP_QUEUE + ".dlq", false);
    }

    @Test
    void PaymentApproved_면_결제대기에서_결제완료() throws Exception {
        insertOrder("PENDING_PAYMENT");

        UUID eventId = send("payment.approved", paymentApproved(16500));

        await(() -> "PAID".equals(status()));
        assertThat(processed(eventId)).isTrue();
    }

    @Test
    void 금액이_다르면_결제완료로_바꾸지_않고_ack() throws Exception {
        insertOrder("PENDING_PAYMENT");

        UUID eventId = send("payment.approved", paymentApproved(9999));

        await(() -> processed(eventId));
        assertThat(status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void 같은_이벤트가_두_번_오면_한_번만_반영한다() throws Exception {
        insertOrder("PENDING_PAYMENT");
        UUID eventId = send("payment.approved", paymentApproved(16500));
        await(() -> "PAID".equals(status()));

        // 다시 결제대기로 돌려 두고 같은 messageId 로 재전송 → 무시되어야 한다
        jdbcTemplate.update("UPDATE orders.orders SET status = 'PENDING_PAYMENT' WHERE id = 1");
        send("payment.approved", paymentApproved(16500), eventId);
        Thread.sleep(2_000);

        assertThat(status()).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders.processed_event", Integer.class))
                .isOne();
    }

    @Test
    void DeliveryAssigned_면_라이더_이름을_저장한다() throws Exception {
        insertOrder("COOKING");

        send("delivery.assigned", """
                {"deliveryId":5,"orderId":1,"customerId":7,"ownerId":10,"riderId":30,"riderName":"이라이더",
                 "assignedAt":"2026-10-26T12:10:00+09:00","customerEmail":"c@x","ownerEmail":"o@x"}
                """);

        await(() -> "이라이더".equals(
                jdbcTemplate.queryForObject("SELECT rider_name FROM orders.orders WHERE id = 1", String.class)));
        assertThat(status()).isEqualTo("COOKING");
    }

    @Test
    void 픽업이면_배달중_배달완료면_배달완료() throws Exception {
        insertOrder("COOKING");

        send("delivery.picked_up", pickedUp());
        await(() -> "DELIVERING".equals(status()));

        send("delivery.completed", """
                {"deliveryId":5,"orderId":1,"customerId":7,"ownerId":10,"riderId":30,
                 "completedAt":"2026-10-26T12:40:00+09:00","customerEmail":"c@x","ownerEmail":"o@x"}
                """);
        await(() -> "DELIVERED".equals(status()));
    }

    @Test
    void 기대한_이전_상태가_아니면_무시하고_ack() throws Exception {
        // 조리중이 아닌데 픽업 이벤트
        insertOrder("PAID");

        UUID eventId = send("delivery.picked_up", pickedUp());

        await(() -> processed(eventId));
        assertThat(status()).isEqualTo("PAID");
    }

    @Test
    void messageId_가_없으면_재시도_후_DLQ() throws Exception {
        insertOrder("COOKING");

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(RabbitConfig.EVENTS_EXCHANGE, "delivery.picked_up",
                new Message(pickedUp().getBytes(StandardCharsets.UTF_8), properties));

        // retry 3회(1s → 2s) 후 order.dlx → order.delivery-picked-up.dlq
        Message dead = rabbitTemplate.receive(RabbitConfig.DELIVERY_PICKED_UP_QUEUE + ".dlq", 15_000);
        assertThat(dead).isNotNull();
        assertThat(status()).isEqualTo("COOKING");
    }

    private static String paymentApproved(int amount) {
        return """
                {"paymentId":3,"orderId":1,"customerId":7,"ownerId":10,"storeId":1,"storeName":"싹분식",
                 "amount":%d,"approvedAt":"2026-10-26T12:00:00+09:00","customerEmail":"c@x","ownerEmail":"o@x"}
                """.formatted(amount);
    }

    private static String pickedUp() {
        return """
                {"deliveryId":5,"orderId":1,"customerId":7,"riderId":30,
                 "pickedUpAt":"2026-10-26T12:20:00+09:00","customerEmail":"c@x"}
                """;
    }

    private UUID send(String routingKey, String body) {
        return send(routingKey, body, UUID.randomUUID());
    }

    // 발행 서비스의 OutboxPublisher 와 같은 헤더 (docs 3-1)
    private UUID send(String routingKey, String body, UUID eventId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(eventId.toString());
        properties.setHeader("eventVersion", 1);
        rabbitTemplate.send(RabbitConfig.EVENTS_EXCHANGE, routingKey,
                new Message(body.getBytes(StandardCharsets.UTF_8), properties));
        return eventId;
    }

    private void insertOrder(String status) {
        jdbcTemplate.update("""
                INSERT INTO orders.orders (customer_id, store_id, owner_id, store_name, status, delivery_address,
                    delivery_dong, pickup_address, customer_phone, customer_email, owner_email,
                    items_amount, delivery_fee, total_amount)
                VALUES (7, 1, 10, '싹분식', ?, '서울시 마포구 2', '연남동', '서울시 마포구 1', '010-1234-5678',
                    'customer@baedalsak.click', 'owner@baedalsak.click', 13500, 3000, 16500)
                """, status);
    }

    private String status() {
        return jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = 1", String.class);
    }

    private boolean processed(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.processed_event WHERE event_id = ?", Integer.class, eventId) == 1;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
