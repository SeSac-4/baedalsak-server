package com.baedalsak.member.notification;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.member.domain.SmsLog;
import com.baedalsak.member.repository.SmsLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * sms_log 선점 · 완료 기록 (#182). 문자 발송(SNS 호출) 중에 DB 트랜잭션을 잡지 않도록 둘을 따로 짧게 나눈다.
 */
@Component
@RequiredArgsConstructor
public class SmsLogWriter {

  private final SmsLogRepository smsLogRepository;

  /**
   * 보낼지 결정. 처음이면 SENDING 으로 남기고 true, 이전 시도가 SENDING 에서 멈췄으면 true(다시 보냄), 이미 SENT 면 false.
   * SENDING 재발송으로 최대 1통 중복될 수 있다 (메일과 같은 규칙).
   */
  @Transactional
  public boolean claim(UUID eventId, String eventType, long memberId) {
    return smsLogRepository.findByEventId(eventId)
        .map(log -> !log.isSent())
        .orElseGet(() -> {
          smsLogRepository.saveAndFlush(SmsLog.sending(eventId, eventType, memberId));
          return true;
        });
  }

  @Transactional
  public void markSent(UUID eventId) {
    smsLogRepository.findByEventId(eventId).ifPresent(SmsLog::markSent);   // 변경 감지로 UPDATE
  }
}