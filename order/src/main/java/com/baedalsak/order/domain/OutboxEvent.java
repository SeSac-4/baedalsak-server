package com.baedalsak.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이벤트 발행 Outbox (outbox 테이블). 상태 변경과 같은 트랜잭션에 저장하고, OutboxPublisher 가 1초마다 RabbitMQ 로 보낸다 (docs 3-2·3-3).
 * event_id 는 RabbitMQ messageId 로 전달되어 받는 쪽 멱등 키가 된다.
 */
@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID eventId;

    // OrderAccepted 등
    @Column(nullable = false, length = 50, updatable = false)
    private String eventType;

    // order.accepted 등
    @Column(nullable = false, length = 50, updatable = false)
    private String routingKey;

    // orderId
    @Column(nullable = false, updatable = false)
    private Long aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    // 분산 추적 ID (N5.2)
    @Column(length = 32, updatable = false)
    private String traceId;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // NULL 이면 미발행
    private OffsetDateTime publishedAt;

    private OutboxEvent(String eventType, String routingKey, Long aggregateId, String payload, String traceId) {
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.traceId = traceId;
    }

    public static OutboxEvent create(String eventType, String routingKey, Long aggregateId, String payload,
                                     String traceId) {
        return new OutboxEvent(eventType, routingKey, aggregateId, payload, traceId);
    }

    // 발행 확인(ack)을 받았을 때만 호출 (docs 3-2)
    public void markPublished() {
        this.publishedAt = OffsetDateTime.now();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
