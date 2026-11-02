package com.baedalsak.delivery.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 배달 메일 리스너 3개(배차완료·배달시작·배달완료)가 함께 쓰는 발송 절차 (#108, docs 10-4).
 * 수신자별로 mail_log 선점 → 동기 발송 → SENT. 재시도 때는 SENT 인 수신자를 건너뛰고 SENDING 인 수신자만 다시 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailDispatcher {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MailLogWriter mailLogWriter;
    private final MailSender mailSender;

    public void send(UUID eventId, String recipient, String templateName, Map<String, String> data) {
        // 이메일 스냅샷이 없는 배달(V2 이전 행, POST /v1/deliveries 이메일 생략)은 재시도해도 소용없고 다른 수신자까지 막으므로 건너뜀
        if (recipient == null || recipient.isBlank()) {
            log.warn("수신자 이메일 없음, 메일 건너뜀 eventId={}, template={}", eventId, templateName);
            return;
        }
        if (!mailLogWriter.claim(eventId, recipient)) {
            log.info("이미 보낸 메일 건너뜀 eventId={}, template={}", eventId, templateName);
            return;
        }
        try {
            mailSender.send(recipient, templateName, data);
        } catch (PermanentMailFailureException e) {
            // 다시 보내도 실패 → 재시도 없이 DLQ (mail_log 는 SENDING 으로 남음, DLQ 에서 수동 확인)
            throw new AmqpRejectAndDontRequeueException("메일 영구 실패 eventId=" + eventId + ", template=" + templateName, e);
        }
        mailLogWriter.markSent(eventId, recipient);
    }

    // messageId(=eventId)가 없거나 UUID 가 아니면 멱등 처리를 할 수 없으므로 거부 → DLQ (docs 3-3)
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

    // 메일에 보이는 시각은 KST (이벤트는 UTC 로 올 수 있음)
    public static String formatKst(OffsetDateTime time) {
        return time.atZoneSameInstant(KST).format(TIME_FORMAT);
    }
}
