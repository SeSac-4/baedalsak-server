package com.baedalsak.payment.service;

import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossCancelClient;
import com.baedalsak.payment.client.toss.TossCancelResponse;
import com.baedalsak.payment.domain.CancelRequester;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.event.CancelPaymentCommand;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import com.baedalsak.payment.repository.PaymentRepository;
import com.baedalsak.payment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 결제 취소 명령 처리 (#127). 결제 상태로 판단(같은 결제에 명령이 또 와도 토스 환불 1번 · 취소 행 1개),
 * 토스 결과 분류(성공 · 다시 시도 · 조회 후 판단 · 확정 거절), 영구 오류는 바로 DLQ
 */
@ExtendWith(MockitoExtension.class)
class PaymentCancelServiceTest {

    static final UUID MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final OffsetDateTime CANCELED_AT = OffsetDateTime.parse("2026-11-06T12:00:00+09:00");
    static final CancelPaymentCommand OWNER_REJECT = new CancelPaymentCommand(1005L, 7L, "재료 소진", "OWNER");

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentCancelRepository paymentCancelRepository;
    @Mock
    ProcessedEventRepository processedEventRepository;
    @Mock
    PaymentCancelRecorder recorder;
    @Mock
    TossCancelClient tossCancelClient;

    PaymentCancelService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCancelService(paymentRepository, paymentCancelRepository, processedEventRepository,
                recorder, tossCancelClient, "주문 취소에 따른 결제 취소");
    }

    // ---------- 정상 ----------

    @Test
    void 승인_완료_결제를_환불_처리_중으로_바꾸고_토스_취소_후_완료를_기록한다() {
        Payment approved = payment(PaymentStatus.APPROVED);
        PaymentCancel cancel = cancel(approved);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(approved));
        given(recorder.start(approved, "재료 소진", CancelRequester.OWNER)).willReturn(Optional.of(cancel));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", cancel.getIdempotencyKey(), 7L)).willReturn(canceled());

        service.handle(MESSAGE_ID, OWNER_REJECT);

        verify(recorder).countAttempt(cancel.getId());
        verify(recorder).complete(approved, cancel, "tx_cancel_1", CANCELED_AT, MESSAGE_ID);
    }

    @Test
    void 이미_처리한_메시지면_아무것도_하지_않는다() {
        given(processedEventRepository.existsById(MESSAGE_ID)).willReturn(true);

        service.handle(MESSAGE_ID, OWNER_REJECT);

        verifyNoInteractions(paymentRepository, recorder, tossCancelClient);
    }

    @Test
    void 결제가_없는_주문이면_로그만_남기고_ack() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty());

        service.handle(MESSAGE_ID, OWNER_REJECT);

        verifyNoInteractions(recorder, tossCancelClient);
    }

    // ---------- 같은 결제에 명령이 또 옴: 결제 상태로 판단 ----------

    @Test
    void 환불_처리_중이고_진행_중_취소가_있으면_같은_멱등_키로_이어서_부른다() {
        Payment requested = payment(PaymentStatus.CANCEL_REQUESTED);
        PaymentCancel pending = cancel(requested);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(requested));
        given(paymentCancelRepository.findByPaymentIdAndStatus(7L, PaymentCancelStatus.PENDING))
                .willReturn(Optional.of(pending));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", pending.getIdempotencyKey(), 7L)).willReturn(canceled());

        // 보상 · 안전망처럼 메시지 ID 가 다른 두 번째 명령
        service.handle(UUID.randomUUID(), new CancelPaymentCommand(1005L, null, null, "SYSTEM"));

        verify(recorder, never()).start(any(), anyString(), any());
        verify(recorder).complete(any(), any(), any(), any(), any());
    }

    @Test
    void 두_파드가_동시에_받아_시작이_0행이면_다시_읽어_진행_중_취소로_이어간다() {
        Payment approved = payment(PaymentStatus.APPROVED);
        Payment nowRequested = payment(PaymentStatus.CANCEL_REQUESTED);
        PaymentCancel pending = cancel(nowRequested);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(approved));
        given(recorder.start(approved, "재료 소진", CancelRequester.OWNER)).willReturn(Optional.empty());
        given(paymentRepository.findById(7L)).willReturn(Optional.of(nowRequested));
        given(paymentCancelRepository.findByPaymentIdAndStatus(7L, PaymentCancelStatus.PENDING))
                .willReturn(Optional.of(pending));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", pending.getIdempotencyKey(), 7L)).willReturn(canceled());

        service.handle(MESSAGE_ID, OWNER_REJECT);

        verify(recorder).complete(any(Payment.class), org.mockito.ArgumentMatchers.eq(pending),
                org.mockito.ArgumentMatchers.eq("tx_cancel_1"), org.mockito.ArgumentMatchers.eq(CANCELED_AT),
                org.mockito.ArgumentMatchers.eq(MESSAGE_ID));
        verify(recorder).countAttempt(pending.getId());
    }

    @Test
    void 환불_처리_중인데_진행_중_취소가_없으면_이전_확정_거절이라_로그_후_ack() {
        Payment requested = payment(PaymentStatus.CANCEL_REQUESTED);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(requested));
        given(paymentCancelRepository.findByPaymentIdAndStatus(7L, PaymentCancelStatus.PENDING))
                .willReturn(Optional.empty());

        service.handle(MESSAGE_ID, OWNER_REJECT);

        verifyNoInteractions(tossCancelClient);
        verify(recorder, never()).start(any(), anyString(), any());
    }

    @Test
    void 이미_환불_완료면_ack() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(payment(PaymentStatus.CANCELED)));

        service.handle(MESSAGE_ID, OWNER_REJECT);

        verifyNoInteractions(recorder, tossCancelClient);
    }

    // ---------- 영구 오류: 바로 DLQ ----------

    @Test
    void 승인_전_결제면_환불하지_않고_바로_DLQ() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(payment(PaymentStatus.REQUESTED)));

        assertThatThrownBy(() -> service.handle(MESSAGE_ID, OWNER_REJECT))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(recorder, tossCancelClient);
    }

    @Test
    void paymentId_가_주문의_결제와_다르면_DLQ() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(payment(PaymentStatus.APPROVED)));

        assertThatThrownBy(() -> service.handle(MESSAGE_ID, new CancelPaymentCommand(1005L, 99L, null, "OWNER")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(recorder, tossCancelClient);
    }

    @Test
    void requestedBy_가_올바르지_않으면_DLQ() {
        assertThatThrownBy(() -> service.handle(MESSAGE_ID, new CancelPaymentCommand(1005L, null, null, "ADMIN")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> service.handle(MESSAGE_ID, new CancelPaymentCommand(1005L, null, null, null)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(paymentRepository, tossCancelClient);
    }

    @Test
    void orderId_가_없으면_DLQ() {
        assertThatThrownBy(() -> service.handle(MESSAGE_ID, new CancelPaymentCommand(null, 7L, null, "OWNER")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    // ---------- 토스 결과 분류 ----------

    @Test
    void 토스_응답이_없으면_다시_시도하도록_던지고_기록하지_않는다() {
        PaymentCancel cancel = givenStarted();
        given(tossCancelClient.cancel(anyString(), anyString(), anyString(), anyLong()))
                .willThrow(TossApiException.noResponse(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> service.handle(MESSAGE_ID, OWNER_REJECT))
                .isInstanceOf(TossCancelRetryException.class);
        verify(recorder).countAttempt(cancel.getId());
        verify(recorder, never()).complete(any(), any(), any(), any(), any());
        verify(recorder, never()).fail(any(), any(), any(), any(), any());
    }

    @Test
    void 토스_5xx_PROVIDER_ERROR_는_다시_시도() {
        givenStarted();
        given(tossCancelClient.cancel(anyString(), anyString(), anyString(), anyLong()))
                .willThrow(TossApiException.rejected(500, "PROVIDER_ERROR", "일시적인 오류"));

        assertThatThrownBy(() -> service.handle(MESSAGE_ID, OWNER_REJECT))
                .isInstanceOf(TossCancelRetryException.class);
    }

    @Test
    void 이미_취소된_결제_에러면_토스_조회로_취소를_확인해_완료로_기록한다() {
        PaymentCancel cancel = givenStarted();
        given(tossCancelClient.cancel(anyString(), anyString(), anyString(), anyLong()))
                .willThrow(TossApiException.rejected(400, "ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다."));
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(canceled());

        service.handle(MESSAGE_ID, OWNER_REJECT);

        verify(recorder).complete(any(), any(PaymentCancel.class), any(), any(), any());
        assertThat(cancel.getIdempotencyKey()).isNotBlank();
    }

    @Test
    void 조회_후_판단_에러인데_토스가_취소가_아니면_확인이_필요해_DLQ() {
        givenStarted();
        given(tossCancelClient.cancel(anyString(), anyString(), anyString(), anyLong()))
                .willThrow(TossApiException.rejected(403, "NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));
        given(tossCancelClient.getPayment("tgen_key", 7L))
                .willReturn(new TossCancelResponse("tgen_key", "BDS-1005-1", "DONE", 18000, List.of()));

        assertThatThrownBy(() -> service.handle(MESSAGE_ID, OWNER_REJECT))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(recorder, never()).complete(any(), any(), any(), any(), any());
    }

    @Test
    void 확정_거절이면_취소_실패와_PaymentCancelFailed_를_기록하고_ack() {
        PaymentCancel cancel = givenStarted();
        given(tossCancelClient.cancel(anyString(), anyString(), anyString(), anyLong()))
                .willThrow(TossApiException.rejected(400, "INVALID_REQUEST", "잘못된 요청입니다."));

        service.handle(MESSAGE_ID, OWNER_REJECT);

        verify(recorder).fail(any(Payment.class), org.mockito.ArgumentMatchers.eq(cancel),
                org.mockito.ArgumentMatchers.eq("INVALID_REQUEST"), org.mockito.ArgumentMatchers.eq("잘못된 요청입니다."),
                org.mockito.ArgumentMatchers.eq(MESSAGE_ID));
    }

    // ---------- 사유 ----------

    @Test
    void 사유가_없으면_기본_문구_200자가_넘으면_자른다() {
        assertThat(service.normalizeReason(null)).isEqualTo("주문 취소에 따른 결제 취소");
        assertThat(service.normalizeReason("  ")).isEqualTo("주문 취소에 따른 결제 취소");
        assertThat(service.normalizeReason("가".repeat(250))).hasSize(200);
        assertThat(service.normalizeReason(" 재료 소진 ")).isEqualTo("재료 소진");
    }

    private PaymentCancel givenStarted() {
        Payment approved = payment(PaymentStatus.APPROVED);
        PaymentCancel cancel = cancel(approved);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(approved));
        given(recorder.start(approved, "재료 소진", CancelRequester.OWNER)).willReturn(Optional.of(cancel));
        return cancel;
    }

    private static TossCancelResponse canceled() {
        return new TossCancelResponse("tgen_key", "BDS-1005-1", "CANCELED", 18000,
                List.of(new TossCancelResponse.Cancel(18000, CANCELED_AT, "tx_cancel_1", "DONE")));
    }

    private static PaymentCancel cancel(Payment payment) {
        PaymentCancel cancel = PaymentCancel.fullCancel(payment, "재료 소진", CancelRequester.OWNER);
        ReflectionTestUtils.setField(cancel, "id", 3L);
        return cancel;
    }

    private static Payment payment(PaymentStatus status) {
        Payment payment = Payment.request(1005L, 1L, 10L, 3L, "싹분식", "customer@test.com", "owner@test.com",
                "BDS-1005-1", 18000);
        ReflectionTestUtils.setField(payment, "id", 7L);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "tossPaymentKey", "tgen_key");
        return payment;
    }
}
