package com.baedalsak.member.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알림 문자 발송 기록 (#182) — event_id 하나당 한 행. 중복 발송 방지용 */
@Entity
@Table(name = "sms_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsLog {

  public enum Status { SENDING, SENT }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "event_type", nullable = false, length = 30)
  private String eventType;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Status status;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;   // DB 기본값 now()

  @Column(name = "sent_at")
  private OffsetDateTime sentAt;

  /** 선점: 보내기 전에 SENDING 으로 한 줄 남긴다 */
  public static SmsLog sending(UUID eventId, String eventType, long memberId) {
    SmsLog log = new SmsLog();
    log.eventId = eventId;
    log.eventType = eventType;
    log.memberId = memberId;
    log.status = Status.SENDING;
    return log;
  }

  public void markSent() {
    this.status = Status.SENT;
    this.sentAt = OffsetDateTime.now();
  }

  public boolean isSent() {
    return status == Status.SENT;
  }
}