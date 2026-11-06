package com.baedalsak.payment.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * processed_event 테이블 (#127, order · delivery 와 같은 구조). 처리를 끝낸 명령의 메시지 ID 를 남긴다.
 * INSERT 는 ProcessedEventRepository.insertIfAbsent 로만 한다.
 */
@Entity
@Table(name = "processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

  @Id
  private UUID eventId;

  @Column(nullable = false, length = 50)
  private String eventType;

  @Column(nullable = false, insertable = false, updatable = false)
  private OffsetDateTime processedAt;
}
