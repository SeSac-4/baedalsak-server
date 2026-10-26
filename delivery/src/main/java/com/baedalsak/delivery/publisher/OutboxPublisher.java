package com.baedalsak.delivery.publisher;

import com.baedalsak.delivery.config.RabbitConfig;
import com.baedalsak.delivery.domain.Outbox;
import com.baedalsak.delivery.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 발행기 (docs 3-2). 1초마다 미발행 행을 baedalsak.events 로 보내고, 발행 확인(ack)을 받은 행만 published_at 을 기록한다.
 * nack·시간 초과·return(받을 큐 없음)이면 기록하지 않음 → 다음 폴링에 다시 보낸다.
 * 같은 행이 두 번 나갈 수 있으므로(ack 직후 파드 종료) 구독자 멱등이 필수다.
 * outbox.publisher.enabled=false 로 끌 수 있다 (테스트).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;
    private static final int EVENT_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<Outbox> rows = outboxRepository.findUnpublishedForUpdate(BATCH_SIZE);
        if (rows.isEmpty()) {
            return;
        }

        // 먼저 모두 보내고 확인은 나중에 모아서 기다린다 → 행마다 왕복을 기다리지 않음
        List<CorrelationData> sent = new ArrayList<>(rows.size());
        for (Outbox row : rows) {
            sent.add(send(row));
        }
        for (int i = 0; i < rows.size(); i++) {
            if (sent.get(i) != null && confirmed(rows.get(i), sent.get(i))) {
                rows.get(i).markPublished();   // 트랜잭션 커밋 시 UPDATE outbox SET published_at
            }
        }
    }

    // 브로커 연결 실패 등으로 보내지 못하면 null → 이번 폴링에서는 기록하지 않음
    private CorrelationData send(Outbox row) {
        String eventId = row.getEventId().toString();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(eventId);                       // 구독자 processed_event 멱등 키
        properties.setHeader("eventType", row.getEventType());
        properties.setHeader("eventVersion", EVENT_VERSION);
        properties.setHeader("occurredAt", row.getCreatedAt().toString());
        properties.setHeader("traceId", row.getTraceId());     // 요청 당시 traceId (발행기 스레드의 traceId 와 다름)
        Message message = new Message(row.getPayload().getBytes(StandardCharsets.UTF_8), properties);

        CorrelationData correlation = new CorrelationData(eventId);
        try {
            rabbitTemplate.send(RabbitConfig.EVENTS_EXCHANGE, row.getRoutingKey(), message, correlation);
            return correlation;
        } catch (AmqpException e) {
            log.warn("outbox send failed: eventId={}, cause={}", eventId, e.toString());
            return null;
        }
    }

    private boolean confirmed(Outbox row, CorrelationData correlation) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (correlation.getReturned() != null) {
                // 라우팅 키에 바인딩된 큐가 없음 (구독 서비스 미기동·바인딩 누락)
                log.error("outbox returned (no queue): eventId={}, eventType={}, routingKey={}",
                        row.getEventId(), row.getEventType(), row.getRoutingKey());
                return false;
            }
            if (!confirm.ack()) {
                log.warn("outbox nack: eventId={}, reason={}", row.getEventId(), confirm.reason());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {   // 시간 초과 등
            log.warn("outbox confirm failed: eventId={}, cause={}", row.getEventId(), e.toString());
            return false;
        }
    }
}
