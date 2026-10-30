package com.baedalsak.delivery.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 배달 메일 발송 절차 (#108, docs 10-4). 선점 → 발송 → SENT, 이미 보낸 수신자 건너뜀, 영구 오류·eventId 오류는 DLQ
 */
@ExtendWith(MockitoExtension.class)
class MailDispatcherTest {

    static final UUID EVENT_ID = UUID.fromString("0b6f2a4e-3c1d-4f5a-9e8b-7d6c5b4a3f21");
    static final String RECIPIENT = "customer@example.com";
    static final Map<String, String> DATA = Map.of("orderId", "31");

    @Mock
    MailLogWriter mailLogWriter;
    @Mock
    MailSender mailSender;
    @InjectMocks
    MailDispatcher mailDispatcher;

    @Test
    void 선점하면_보내고_SENT_로_기록한다() {
        given(mailLogWriter.claim(EVENT_ID, RECIPIENT)).willReturn(true);

        mailDispatcher.send(EVENT_ID, RECIPIENT, "template", DATA);

        verify(mailSender).send(RECIPIENT, "template", DATA);
        verify(mailLogWriter).markSent(EVENT_ID, RECIPIENT);
    }

    @Test
    void 이미_SENT_면_보내지_않는다() {
        given(mailLogWriter.claim(EVENT_ID, RECIPIENT)).willReturn(false);

        mailDispatcher.send(EVENT_ID, RECIPIENT, "template", DATA);

        verifyNoInteractions(mailSender);
        verify(mailLogWriter, never()).markSent(any(), any());
    }

    @Test
    void 수신자_이메일이_없으면_선점도_발송도_하지_않는다() {
        mailDispatcher.send(EVENT_ID, null, "template", DATA);
        mailDispatcher.send(EVENT_ID, " ", "template", DATA);

        verifyNoInteractions(mailLogWriter, mailSender);
    }

    @Test
    void 영구_오류면_재시도_없이_거부하고_SENT_로_기록하지_않는다() {
        given(mailLogWriter.claim(EVENT_ID, RECIPIENT)).willReturn(true);
        willThrow(new PermanentMailFailureException("rejected", new RuntimeException()))
                .given(mailSender).send(any(), any(), any());

        assertThatThrownBy(() -> mailDispatcher.send(EVENT_ID, RECIPIENT, "template", DATA))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(mailLogWriter, never()).markSent(any(), any());
    }

    @Test
    void 일시_오류는_그대로_던져_재시도에_맡긴다() {
        given(mailLogWriter.claim(EVENT_ID, RECIPIENT)).willReturn(true);
        willThrow(new IllegalStateException("throttled")).given(mailSender).send(any(), any(), any());

        assertThatThrownBy(() -> mailDispatcher.send(EVENT_ID, RECIPIENT, "template", DATA))
                .isInstanceOf(IllegalStateException.class);

        verify(mailLogWriter, never()).markSent(any(), any());
    }

    @Test
    void messageId_를_eventId_로_읽는다() {
        assertThat(MailDispatcher.parseEventId("DeliveryAssigned", EVENT_ID.toString())).isEqualTo(EVENT_ID);
    }

    @Test
    void messageId_가_없거나_UUID_가_아니면_거부한다() {
        assertThatThrownBy(() -> MailDispatcher.parseEventId("DeliveryAssigned", null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> MailDispatcher.parseEventId("DeliveryAssigned", "not-a-uuid"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void UTC_시각을_KST_로_표시한다() {
        assertThat(MailDispatcher.formatKst(OffsetDateTime.parse("2026-10-27T06:30:00Z"))).isEqualTo("2026-10-27 15:30");
    }
}
