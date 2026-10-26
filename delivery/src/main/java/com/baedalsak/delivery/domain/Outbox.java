package com.baedalsak.delivery.domain;

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
 * outbox 테이블. 상태 변경과 같은 트랜잭션에 INSERT 하고, OutboxPublisher 가 1초마다 RabbitMQ 로 보낸다 (docs 3-2).
 * published_at 이 NULL 이면 아직 발행 확인(ack)을 받지 못한 행.
 */
@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RabbitMQ messageId 로 전달 → 구독자 멱등 키 (processed_event)
    @Column(nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(nullable = false, length = 50, updatable = false)
    private String eventType;

    @Column(nullable = false, length = 50, updatable = false)
    private String routingKey;

    @Column(nullable = false, updatable = false)
    private Long aggregateId;

    // 이미 직렬화된 JSON 문자열을 JSONB 로 저장. 발행기는 이 문자열을 그대로 메시지 본문으로 보낸다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(length = 32, updatable = false)
    private String traceId;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime publishedAt;

    public static Outbox of(String eventType, String routingKey, Long aggregateId, String payload, String traceId) {
        Outbox outbox = new Outbox();
        outbox.eventId = UUID.randomUUID();
        outbox.eventType = eventType;
        outbox.routingKey = routingKey;
        outbox.aggregateId = aggregateId;
        outbox.payload = payload;
        outbox.traceId = traceId;
        return outbox;
    }

    public void markPublished() {
        this.publishedAt = OffsetDateTime.now();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
