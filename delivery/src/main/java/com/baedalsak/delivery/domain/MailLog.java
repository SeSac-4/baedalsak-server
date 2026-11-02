package com.baedalsak.delivery.domain;

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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * mail_log 테이블. 메일 발송 기록 (이벤트 1건 × 수신자 1명 = 1행).
 * 선점·완료 표시는 MailLogRepository 의 쿼리로만 한다.
 */
@Entity
@Table(name = "mail_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MailLogStatus status;

    @Column(nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime sentAt;
}
