package com.baedalsak.member.notification;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;

import com.baedalsak.member.config.SmsProperties;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.repository.MemberRepository;
import com.baedalsak.member.sms.SmsSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 · 배달 알림 문자 (#182). 휴대폰 인증한 고객에게만, 이벤트당 1통.
 * 문자 실패(503)는 예외를 그대로 던져 재시도 → 3번 실패하면 DLQ. 재시도해도 소용없는 경우는 로그만 남기고 끝낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsNotificationService {

  private final SmsProperties props;
  private final MemberRepository memberRepository;
  private final SmsLogWriter smsLogWriter;
  private final SmsSender smsSender;

  public void send(UUID eventId, String eventType, Long customerId, String message) {
    // ① 스위치 (월 한도 1달러 — 필요하면 SMS_NOTIFICATION_ENABLED=false)
    if (!props.notificationEnabled()) {
      return;
    }
    if (customerId == null) {
      throw new AmqpRejectAndDontRequeueException(eventType + " without customerId eventId=" + eventId);
    }

    // ② 휴대폰 인증한 고객만. 번호는 이벤트가 아니라 member DB 의 값 (인증한 번호)
    Member member = memberRepository.findById(customerId).orElse(null);
    if (member == null || !member.isPhoneVerified()) {
      return;
    }

    // ③ 이미 보낸 이벤트면 건너뜀 (재전달 · 재시도)
    if (!smsLogWriter.claim(eventId, eventType, customerId)) {
      log.info("이미 보낸 알림 문자 건너뜀 eventId={}, type={}", eventId, eventType);
      return;
    }

    // ④ 발송 → 기록. 실패하면 BusinessException(503) → 재시도 때 SENDING 이라 다시 보냄
    smsSender.send(member.getPhone(), message);
    smsLogWriter.markSent(eventId);
  }

  /** messageId(= outbox 의 이벤트 UUID)가 없거나 UUID 가 아니면 중복 확인을 못 함 → 재시도 없이 DLQ (docs 3-3) */
  public static UUID parseEventId(String eventType, String messageId) {
    if (messageId == null) {
      throw new AmqpRejectAndDontRequeueException(eventType + " without messageId");
    }
    try {
      return UUID.fromString(messageId);
    } catch (IllegalArgumentException e) {
      throw new AmqpRejectAndDontRequeueException(eventType + " with invalid messageId: " + messageId, e);
    }
  }
}