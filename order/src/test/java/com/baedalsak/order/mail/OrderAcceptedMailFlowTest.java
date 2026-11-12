package com.baedalsak.order.mail;

import com.baedalsak.order.support.RabbitMQIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 조리 시작 메일 흐름 (#33, docs 10-1). outbox 의 OrderAccepted → 발행기 → 자기 메일 큐 → 리스너 → 발송 → mail_log SENT.
 * 실제 RabbitMQ(컨테이너)를 쓰고, SES 발송기만 mock 이다.
 */
class OrderAcceptedMailFlowTest extends RabbitMQIntegrationTest {

    @Test
    void 주문_수락_이벤트가_발행되면_고객에게_메일을_보내고_SENT_로_남긴다() throws Exception {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders.outbox (event_id, event_type, routing_key, aggregate_id, payload)
                VALUES (?, 'OrderAccepted', 'order.accepted', 31, ?::jsonb)
                """, eventId, """
                {"orderId":31,"storeName":"싹분식","estimatedReadyAt":"2026-10-27T15:30:00+09:00",
                 "customerEmail":"customer@example.com","ownerEmail":"owner@example.com"}
                """);

        verify(mailSender, timeout(10_000)).send(eq("customer@example.com"), eq("order-accepted-customer"),
                eq(Map.of("orderId", "31", "storeName", "싹분식", "estimatedReadyAt", "2026-10-27 15:30")));
        awaitSent(eventId, "customer@example.com");
        // 사장님에게는 보내지 않는다
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders.mail_log WHERE event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
    }

    private void awaitSent(UUID eventId, String recipient) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!"SENT".equals(status(eventId, recipient)) && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(status(eventId, recipient)).isEqualTo("SENT");
    }

    private String status(UUID eventId, String recipient) {
        return jdbcTemplate.query("SELECT status FROM orders.mail_log WHERE event_id = ? AND recipient = ?",
                rs -> rs.next() ? rs.getString(1) : null, eventId, recipient);
    }
}
