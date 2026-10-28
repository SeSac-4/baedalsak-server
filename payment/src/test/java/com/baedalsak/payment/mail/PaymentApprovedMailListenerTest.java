package com.baedalsak.payment.mail;

import com.baedalsak.payment.event.PaymentApprovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 결제완료 메일 리스너 (#37, docs 10번). 수신자별 선점 → 발송 → SENT, 이미 보낸 수신자 건너뜀, 영구 오류·eventId 오류는 DLQ
 */
@ExtendWith(MockitoExtension.class)
class PaymentApprovedMailListenerTest {

    static final String EVENT_ID = "0b6f2a4e-3c1d-4f5a-9e8b-7d6c5b4a3f21";
    static final UUID EVENT_UUID = UUID.fromString(EVENT_ID);
    static final String CUSTOMER = "customer@example.com";
    static final String OWNER = "owner@example.com";

    @Mock
    MailLogWriter mailLogWriter;
    @Mock
    MailSender mailSender;

    PaymentApprovedMailListener listener;

    @BeforeEach
    void setUp() {
        PaymentMailProperties properties = new PaymentMailProperties("ap-northeast-2", "no-reply@baedalsak.click",
                new PaymentMailProperties.Templates("customer-template", "owner-template"));
        listener = new PaymentApprovedMailListener(mailLogWriter, mailSender, properties);
    }

    @Test
    void 고객과_사장님에게_각자_템플릿으로_보내고_SENT_로_기록한다() {
        given(mailLogWriter.claim(EVENT_UUID, CUSTOMER)).willReturn(true);
        given(mailLogWriter.claim(EVENT_UUID, OWNER)).willReturn(true);

        listener.onPaymentApproved(event(), EVENT_ID);

        Map<String, String> data = Map.of("orderId", "31", "storeName", "싹싹분식", "amount", "18,000원",
                "approvedAt", "2026-10-27 15:30");
        verify(mailSender).send(CUSTOMER, "customer-template", data);
        verify(mailSender).send(OWNER, "owner-template", data);
        verify(mailLogWriter).markSent(EVENT_UUID, CUSTOMER);
        verify(mailLogWriter).markSent(EVENT_UUID, OWNER);
    }

    @Test
    void 이미_SENT_인_수신자는_건너뛰고_남은_수신자만_보낸다() {
        given(mailLogWriter.claim(EVENT_UUID, CUSTOMER)).willReturn(false);
        given(mailLogWriter.claim(EVENT_UUID, OWNER)).willReturn(true);

        listener.onPaymentApproved(event(), EVENT_ID);

        verify(mailSender, never()).send(eq(CUSTOMER), any(), any());
        verify(mailSender).send(eq(OWNER), eq("owner-template"), any());
        verify(mailLogWriter, never()).markSent(EVENT_UUID, CUSTOMER);
        verify(mailLogWriter).markSent(EVENT_UUID, OWNER);
    }

    @Test
    void 영구_오류면_재시도_없이_거부하고_SENT_로_기록하지_않는다() {
        given(mailLogWriter.claim(EVENT_UUID, CUSTOMER)).willReturn(true);
        willThrow(new PermanentMailFailureException("rejected", new RuntimeException()))
                .given(mailSender).send(any(), any(), any());

        assertThatThrownBy(() -> listener.onPaymentApproved(event(), EVENT_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(mailLogWriter, never()).markSent(any(), any());
    }

    @Test
    void 일시_오류는_그대로_던져_재시도에_맡긴다() {
        given(mailLogWriter.claim(EVENT_UUID, CUSTOMER)).willReturn(true);
        willThrow(new IllegalStateException("throttled")).given(mailSender).send(any(), any(), any());

        assertThatThrownBy(() -> listener.onPaymentApproved(event(), EVENT_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(mailLogWriter, never()).markSent(any(), any());
    }

    @Test
    void messageId_가_없으면_거부한다() {
        assertThatThrownBy(() -> listener.onPaymentApproved(event(), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(mailLogWriter, mailSender);
    }

    @Test
    void messageId_가_UUID_가_아니면_거부한다() {
        assertThatThrownBy(() -> listener.onPaymentApproved(event(), "not-a-uuid"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(mailLogWriter, mailSender);
    }

    // 승인 시각은 UTC 로 와도 메일에는 KST 로 표시
    private static PaymentApprovedEvent event() {
        return new PaymentApprovedEvent(7L, 31L, 1L, 2L, 3L, "싹싹분식", 18000,
                OffsetDateTime.parse("2026-10-27T06:30:00Z"), CUSTOMER, OWNER);
    }
}
