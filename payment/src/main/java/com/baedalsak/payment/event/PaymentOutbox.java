package com.baedalsak.payment.event;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity 
@Table(name = "payment_outbox")
@Getter 
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutbox {

  @Id 
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false, unique = true)
  private UUID eventId;

  @Column(nullable = false, updatable = false, length = 50)
  private String eventType;
  
  @Column(nullable = false, updatable = false, length = 50)
  private String routingKey;

  @Column(nullable = false, updatable = false)
  private Long aggregateId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(length = 32, updatable = false)
  private String traceId;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  private OffsetDateTime publishedAt;   // null 이면 아직 발행 안 됨

  // 발행 대기 상태의 이벤트를 만든다. 새 ID, 생성 시각을 자동으로 채우고 publishedAt 은 비워 둔다
  public static PaymentOutbox of(String eventType, String routingKey, Long aggregateId, String payload, String traceId ) {
    PaymentOutbox outbox = new PaymentOutbox();
    outbox.eventId = UUID.randomUUID();
    outbox.eventType = Objects.requireNonNull(eventType, "eventType");
    outbox.routingKey = Objects.requireNonNull(routingKey, "routingKey");
    outbox.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
    outbox.payload = Objects.requireNonNull(payload, "payload");
    outbox.traceId = traceId;   // 추적 정보가 없을 수 있어 null 허용
    outbox.createdAt = OffsetDateTime.now();
    return outbox;
  }
}
