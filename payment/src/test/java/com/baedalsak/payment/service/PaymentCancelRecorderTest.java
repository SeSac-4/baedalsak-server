package com.baedalsak.payment.service;

import com.baedalsak.payment.domain.CancelRequester;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.event.OutboxAppender;
import com.baedalsak.payment.event.PaymentCancelFailedEvent;
import com.baedalsak.payment.event.PaymentCanceledEvent;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import com.baedalsak.payment.repository.PaymentRepository;
import com.baedalsak.payment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 결제 취소 DB 기록 (#127). 상태는 조건부 UPDATE, 0행이면 outbox 를 쓰지 않음
 */
@ExtendWith(MockitoExtension.class)
class PaymentCancelRecorderTest {

    static final UUID MESSAGE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final OffsetDateTime CANCELED_AT = OffsetDateTime.parse("2026-11-06T12:00:00+09:00");

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentCancelRepository paymentCancelRepository;
    @Mock
    ProcessedEventRepository processedEventRepository;
    @Mock
    OutboxAppender outboxAppender;
    @InjectMocks
    PaymentCancelRecorder recorder;

    @Test
    void 시작은_승인_완료에서_환불_처리_중으로_바꾸고_전액_진행_중_취소를_저장한다() {
        given(paymentRepository.changeStatus(7L, PaymentStatus.APPROVED, PaymentStatus.CANCEL_REQUESTED)).willReturn(1);
        given(paymentCancelRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PaymentCancel cancel = recorder.start(payment(), "재료 소진", CancelRequester.OWNER).orElseThrow();

        assertThat(cancel.getPaymentId()).isEqualTo(7L);
        assertThat(cancel.getAmount()).isEqualTo(18000);
        assertThat(cancel.getStatus()).isEqualTo(PaymentCancelStatus.PENDING);
        assertThat(cancel.getRequestedBy()).isEqualTo(CancelRequester.OWNER);
        assertThat(cancel.getIdempotencyKey()).hasSize(36);
    }

    @Test
    void 시작이_0행이면_취소_행을_만들지_않는다() {
        given(paymentRepository.changeStatus(7L, PaymentStatus.APPROVED, PaymentStatus.CANCEL_REQUESTED)).willReturn(0);

        assertThat(recorder.start(payment(), "재료 소진", CancelRequester.OWNER)).isEmpty();
        verify(paymentCancelRepository, never()).save(any());
    }

    @Test
    void 완료는_환불_완료_취소_완료_PaymentCanceled_processed_event_를_함께_쓴다() {
        PaymentCancel cancel = cancel();
        given(paymentRepository.changeStatus(7L, PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELED)).willReturn(1);

        assertThat(recorder.complete(payment(), cancel, "tx_1", CANCELED_AT, MESSAGE_ID)).isTrue();

        verify(paymentCancelRepository).markDone(3L, "tx_1", CANCELED_AT, PaymentCancelStatus.PENDING,
                PaymentCancelStatus.DONE);
        verify(outboxAppender).append(PaymentCanceledEvent.TYPE, "payment.canceled", 7L,
                new PaymentCanceledEvent(7L, 1005L, 18000, CANCELED_AT));
        verify(processedEventRepository).insertIfAbsent(MESSAGE_ID, "CancelPayment");
    }

    @Test
    void 완료가_0행이면_이미_다른_처리가_끝낸_것이라_outbox_를_쓰지_않는다() {
        given(paymentRepository.changeStatus(7L, PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELED)).willReturn(0);

        assertThat(recorder.complete(payment(), cancel(), "tx_1", CANCELED_AT, MESSAGE_ID)).isFalse();
        verifyNoInteractions(outboxAppender, processedEventRepository);
    }

    @Test
    void 확정_거절은_취소_실패와_PaymentCancelFailed_를_쓰고_결제는_그대로_둔다() {
        given(paymentCancelRepository.markFailed(3L, PaymentCancelStatus.PENDING, PaymentCancelStatus.FAILED)).willReturn(1);

        assertThat(recorder.fail(payment(), cancel(), "INVALID_REQUEST", "잘못된 요청", MESSAGE_ID)).isTrue();

        verify(outboxAppender).append(PaymentCancelFailedEvent.TYPE, "payment.cancel-failed", 7L,
                new PaymentCancelFailedEvent(7L, 1005L, "INVALID_REQUEST", "잘못된 요청"));
        verify(paymentRepository, never()).changeStatus(anyLong(), any(), any());
        verify(processedEventRepository).insertIfAbsent(MESSAGE_ID, "CancelPayment");
    }

    @Test
    void 확정_거절이_0행이면_outbox_를_쓰지_않는다() {
        given(paymentCancelRepository.markFailed(3L, PaymentCancelStatus.PENDING, PaymentCancelStatus.FAILED)).willReturn(0);

        assertThat(recorder.fail(payment(), cancel(), "INVALID_REQUEST", "잘못된 요청", MESSAGE_ID)).isFalse();
        verify(outboxAppender, never()).append(anyString(), anyString(), anyLong(), any());
    }

    private static PaymentCancel cancel() {
        PaymentCancel cancel = PaymentCancel.fullCancel(payment(), "재료 소진", CancelRequester.OWNER);
        ReflectionTestUtils.setField(cancel, "id", 3L);
        return cancel;
    }

    private static Payment payment() {
        Payment payment = Payment.request(1005L, 1L, 10L, 3L, "싹분식", "customer@test.com", "owner@test.com",
                "BDS-1005-1", 18000);
        ReflectionTestUtils.setField(payment, "id", 7L);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.APPROVED);
        return payment;
    }
}
