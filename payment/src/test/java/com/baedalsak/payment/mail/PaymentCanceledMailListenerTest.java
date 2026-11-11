package com.baedalsak.payment.mail;

import com.baedalsak.payment.event.PaymentCanceledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 환불 완료 메일 리스너 (#180). 고객에게만 선점 → 발송 → SENT, 이미 보낸 메일 건너뜀, SENDING 은 다시 보냄,
 * 영구 오류 · eventId 오류는 DLQ, 사유 한 줄은 requestedBy 로 다르게
 */
@ExtendWith(MockitoExtension.class)
class PaymentCanceledMailListenerTest {

    static final String EVENT_ID = "5c1e9a7b-2d4f-4e8a-b6c3-1f0e9d8c7b6a";
    static final UUID EVENT_UUID = UUID.fromString(EVENT_ID);
    static final String CUSTOMER = "customer@example.com";

    @Mock
    MailLogWriter mailLogWriter;
    @Mock
    MailSender mailSender;

    PaymentCanceledMailListener listener;

    @BeforeEach
    void setUp() {
        PaymentMailProperties properties = new PaymentMailProperties("ap-northeast-2", "no-reply@baedalsak.click",
                new PaymentMailProperties.Templates("customer-template", "owner-template", "canceled-customer-template"));
        listener = new PaymentCanceledMailListener(mailLogWriter, mailSender, properties);
    }

    @Test
    void 고객에게만_환불_완료_템플릿으로_보내고_SENT_로_기록한다() {
        given(mailLogWriter.claim(EVENT_UUID, CUSTOMER)).willReturn(true);

        listener.onPaymentCanceled(event("OWNER"), EVENT_ID);

        Map<String, String> data = Map.of("orderId", "31", "storeName", "싹싹분식", "amount", "18,000원",
                "canceledAt", "2026-11-11 15:30", "reason", "가게 사정으로 주문이 거절되어");
        verify(mailSender).send(CUSTOMER, "canceled-customer-template", data);
        verify(mailLogWriter).markSent(EVENT_UUID, CUSTOMER);
    }

    @Test
    void 이미_SENT_면_보내지_않는다() {
        given(mailLogWriter.claim(EVENT_UUID, CUSTOMER)).willReturn(false);

        listener.onPaymentCanceled(event("OWNER"), EVENT_ID);

        verifyNoInteractions(mailSender);
        verify(mailLogWriter, never()).markSent(any(), any());
    }

    // 선점은 SENDING 이면 다시 true 를 준다 (MailLogWriter 규칙) → 일시 오류 뒤 재전달 때 다시 보낸다
    @Test
    void SENDING_으로_남은_재전달은_다시_보낸다() {
        given(mailLogWriter.claim(EVENT_UUID, CUSTOMER)).willReturn(true);
        willThrow(new IllegalStateException("throttled")).willDoNothing()
                .given(mailSender).send(any(), any(), any());

        assertThatThrownBy(() -> listener.onPaymentCanceled(event("OWNER"), EVENT_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(mailLogWriter, never()).markSent(any(), any());

        listener.onPaymentCanceled(event("OWNER"), EVENT_ID);

        verify(mailSender, times(2)).send(any(), any(), any());
        verify(mailLogWriter).markSent(EVENT_UUID, CUSTOMER);
    }

    @Test
    void 영구_오류면_재시도_없이_거부하고_SENT_로_기록하지_않는다() {
        given(mailLogWriter.claim(EVENT_UUID, CUSTOMER)).willReturn(true);
        willThrow(new PermanentMailFailureException("rejected", new RuntimeException()))
                .given(mailSender).send(any(), any(), any());

        assertThatThrownBy(() -> listener.onPaymentCanceled(event("OWNER"), EVENT_ID))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(mailLogWriter, never()).markSent(any(), any());
    }

    @Test
    void messageId_가_없으면_거부한다() {
        assertThatThrownBy(() -> listener.onPaymentCanceled(event("OWNER"), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(mailLogWriter, mailSender);
    }

    @Test
    void messageId_가_UUID_가_아니면_거부한다() {
        assertThatThrownBy(() -> listener.onPaymentCanceled(event("OWNER"), "not-a-uuid"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(mailLogWriter, mailSender);
    }

    @ParameterizedTest
    @CsvSource({
            "OWNER, 가게 사정으로 주문이 거절되어",
            "CUSTOMER, 주문을 취소하여",
            "SYSTEM, 결제 확인이 늦어져 자동으로"
    })
    void 사유_한_줄은_요청자로_정한다(String requestedBy, String reason) {
        assertThat(PaymentCanceledMailListener.reason(requestedBy)).isEqualTo(reason);
    }

    // 환불 시각은 UTC 로 와도 메일에는 KST 로 표시
    private static PaymentCanceledEvent event(String requestedBy) {
        return new PaymentCanceledEvent(7L, 31L, 18000, OffsetDateTime.parse("2026-11-11T06:30:00Z"),
                CUSTOMER, "싹싹분식", requestedBy);
    }
}
