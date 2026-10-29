package com.baedalsak.payment.mail;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.payment.domain.MailLog;
import com.baedalsak.payment.domain.MailLogStatus;
import com.baedalsak.payment.repository.MailLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * mail_log 선점·완료 기록 (설계서 '이벤트 목록' 메일 리스너, '상태 전이' mail_log.status).
 * SES 호출 중에 DB 트랜잭션을 잡지 않도록 선점과 완료를 각각 짧은 트랜잭션으로 나눈다.
 */
@Component
@RequiredArgsConstructor
public class MailLogWriter {

  private final MailLogRepository mailLogRepository;

  /**
   * 보낼지 결정한다. 새로 선점했거나 SENDING 으로 남아 있으면(이전 시도가 실패·중단) true, 이미 SENT 면 false.
   * SENDING 재발송으로 최대 1회 중복될 수 있다 (메일 특성상 허용).
   */
  @Transactional
  public boolean claim(UUID eventId, String recipient) {
    if (mailLogRepository.insertSending(eventId, recipient) == 1) {
      return true;
    }
    return mailLogRepository.findByEventIdAndRecipient(eventId, recipient)
        .map(MailLog::getStatus)
        .map(status -> status == MailLogStatus.SENDING)
        .orElse(true);
  }

  @Transactional
  public void markSent(UUID eventId, String recipient) {
    mailLogRepository.markSent(eventId, recipient, OffsetDateTime.now(), MailLogStatus.SENT);
  }
}
