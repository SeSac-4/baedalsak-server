package com.baedalsak.payment.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossCancelClient;
import com.baedalsak.payment.client.toss.TossCancelResponse;
import com.baedalsak.payment.domain.CancelRequester;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.response.RecoverResultResponse;
import com.baedalsak.payment.dto.response.RecoverResultResponse.Action;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
 * 재처리 (#153). 결제 하나를 토스에 조회해 DB 상태와 비교 → 환불 처리 중이면 완료 기록 · 토스 취소 다시, 결제 요청됨은 상태만,
 * 그 밖은 409. 토스 IDEMPOTENT_REQUEST_PROCESSING 은 처리 중으로 건너뜀, 토스 조회 실패는 502
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecoverServiceTest {

    static final OffsetDateTime CANCELED_AT = OffsetDateTime.parse("2026-11-06T12:00:00+09:00");

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentCancelRepository paymentCancelRepository;
    @Mock
    PaymentCancelRecorder recorder;
    @Mock
    TossCancelClient tossCancelClient;
    @InjectMocks
    PaymentRecoverService service;

    // ---------- 환불 처리 중 + 토스 취소 → 완료 기록 ----------

    @Test
    void 진행_중_취소인데_토스가_취소면_완료로_기록한다() {
        Payment payment = stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELED);
        PaymentCancel pending = latest(PaymentCancelStatus.PENDING);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("CANCELED"));

        RecoverResultResponse result = service.recover(7L);

        verify(recorder).complete(payment, pending, "tx_cancel_1", CANCELED_AT, null);
        verify(tossCancelClient, never()).cancel(anyString(), anyString(), anyString(), anyLong());
        assertThat(result).isEqualTo(new RecoverResultResponse(7L, 1005L, PaymentStatus.CANCEL_REQUESTED, "CANCELED",
                Action.RECORDED_CANCELED, PaymentStatus.CANCELED));
    }

    @Test
    void 확정_거절_뒤_토스가_실제로_취소면_그_행을_완료로_기록한다() {
        Payment payment = stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELED);
        PaymentCancel failed = latest(PaymentCancelStatus.FAILED);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("CANCELED"));

        assertThat(service.recover(7L).action()).isEqualTo(Action.RECORDED_CANCELED);
        verify(recorder).complete(payment, failed, "tx_cancel_1", CANCELED_AT, null);
        verify(recorder, never()).restart(any(), any());
    }

    // ---------- 환불 처리 중 + 토스 승인 → 토스 취소 다시 ----------

    @Test
    void 진행_중_취소인데_토스가_승인이면_같은_멱등_키로_다시_취소한다() {
        Payment payment = stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELED);
        PaymentCancel pending = latest(PaymentCancelStatus.PENDING);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("DONE"));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", pending.getIdempotencyKey(), 7L)).willReturn(toss("CANCELED"));

        RecoverResultResponse result = service.recover(7L);

        verify(recorder).countAttempt(3L);
        verify(recorder).complete(payment, pending, "tx_cancel_1", CANCELED_AT, null);
        assertThat(result.tossStatus()).isEqualTo("DONE");
        assertThat(result.action()).isEqualTo(Action.CANCELED);
        assertThat(result.after()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void 확정_거절_뒤_토스가_승인이면_새_취소_행의_새_멱등_키로_다시_취소한다() {
        Payment payment = stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELED);
        PaymentCancel failed = latest(PaymentCancelStatus.FAILED);
        PaymentCancel restarted = cancel(payment, 4L, PaymentCancelStatus.PENDING);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("DONE"));
        given(recorder.restart(payment, failed)).willReturn(Optional.of(restarted));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", restarted.getIdempotencyKey(), 7L))
                .willReturn(toss("CANCELED"));

        assertThat(service.recover(7L).action()).isEqualTo(Action.CANCELED);
        assertThat(restarted.getIdempotencyKey()).isNotEqualTo(failed.getIdempotencyKey());
        verify(recorder).countAttempt(4L);
        verify(recorder).complete(payment, restarted, "tx_cancel_1", CANCELED_AT, null);
    }

    @Test
    void 다시_시작을_다른_재처리가_먼저_했으면_처리_중으로_건너뛴다() {
        Payment payment = stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCEL_REQUESTED);
        PaymentCancel failed = latest(PaymentCancelStatus.FAILED);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("DONE"));
        given(recorder.restart(payment, failed)).willThrow(new DataIntegrityViolationException("uk_payment_cancel_pending"));

        assertThat(service.recover(7L).action()).isEqualTo(Action.IN_PROGRESS);
        verify(tossCancelClient, never()).cancel(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void 확정_거절_뒤_다시_취소도_거절되면_취소_실패로_기록한다() {
        Payment payment = stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCEL_REQUESTED);
        PaymentCancel failed = latest(PaymentCancelStatus.FAILED);
        PaymentCancel restarted = cancel(payment, 4L, PaymentCancelStatus.PENDING);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("DONE"));
        given(recorder.restart(payment, failed)).willReturn(Optional.of(restarted));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", restarted.getIdempotencyKey(), 7L))
                .willThrow(TossApiException.rejected(403, "NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액 입니다."));

        assertThat(service.recover(7L).action()).isEqualTo(Action.CANCEL_REJECTED);
        verify(recorder).fail(payment, restarted, "NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액 입니다.", null);
        verify(recorder, never()).complete(any(), any(), any(), any(), any());
    }

    @Test
    void 토스가_같은_취소를_처리_중이면_건너뛰고_상태를_바꾸지_않는다() {
        stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCEL_REQUESTED);
        PaymentCancel pending = latest(PaymentCancelStatus.PENDING);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("DONE"));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", pending.getIdempotencyKey(), 7L))
                .willThrow(TossApiException.rejected(409, "IDEMPOTENT_REQUEST_PROCESSING", "이전 요청이 처리 중입니다."));

        RecoverResultResponse result = service.recover(7L);

        assertThat(result.action()).isEqualTo(Action.IN_PROGRESS);
        assertThat(result.after()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verify(recorder, never()).complete(any(), any(), any(), any(), any());
        verify(recorder, never()).fail(any(), any(), any(), any(), any());
    }

    @Test
    void 토스_취소_결과를_모르면_진행_중_그대로_둔다() {
        stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCEL_REQUESTED);
        PaymentCancel pending = latest(PaymentCancelStatus.PENDING);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("DONE"));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", pending.getIdempotencyKey(), 7L))
                .willThrow(TossApiException.noResponse(new SocketTimeoutException("read timed out")));

        assertThat(service.recover(7L).action()).isEqualTo(Action.UNKNOWN);
        verify(recorder, never()).complete(any(), any(), any(), any(), any());
        verify(recorder, never()).fail(any(), any(), any(), any(), any());
    }

    @Test
    void 토스가_이미_취소됨으로_답하면_다시_조회해_취소면_완료로_기록한다() {
        Payment payment = stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELED);
        PaymentCancel pending = latest(PaymentCancelStatus.PENDING);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("DONE"), toss("CANCELED"));
        given(tossCancelClient.cancel("tgen_key", "재료 소진", pending.getIdempotencyKey(), 7L))
                .willThrow(TossApiException.rejected(400, "ALREADY_CANCELED_PAYMENT", "이미 취소된 결제 입니다."));

        assertThat(service.recover(7L).action()).isEqualTo(Action.RECORDED_CANCELED);
        verify(recorder).complete(payment, pending, "tx_cancel_1", CANCELED_AT, null);
    }

    @Test
    void 환불_처리_중인데_토스가_승인_취소가_아니면_바꾸지_않고_확인_필요() {
        stored(PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCEL_REQUESTED);
        latest(PaymentCancelStatus.PENDING);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("PARTIAL_CANCELED"));

        assertThat(service.recover(7L).action()).isEqualTo(Action.CHECK_NEEDED);
        verify(tossCancelClient, never()).cancel(anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(recorder);
    }

    // ---------- 결제 요청됨 · 그 밖 ----------

    @Test
    void 결제_요청됨은_바꾸지_않고_토스_상태만_돌려준다() {
        stored(PaymentStatus.REQUESTED, PaymentStatus.REQUESTED);
        given(tossCancelClient.getPayment("tgen_key", 7L)).willReturn(toss("DONE"));

        RecoverResultResponse result = service.recover(7L);

        assertThat(result).isEqualTo(new RecoverResultResponse(7L, 1005L, PaymentStatus.REQUESTED, "DONE",
                Action.NO_CHANGE, PaymentStatus.REQUESTED));
        verifyNoInteractions(recorder, paymentCancelRepository);
        verify(tossCancelClient, never()).cancel(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void 키_없는_결제_요청은_토스에_조회할_결제가_없어_409() {
        Payment payment = payment(PaymentStatus.REQUESTED);
        ReflectionTestUtils.setField(payment, "tossPaymentKey", null);
        given(paymentRepository.findById(7L)).willReturn(Optional.of(payment));

        assertInvalidStatus();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"APPROVED", "CANCELED", "EXPIRED"})
    void 환불_처리_중_결제_요청됨이_아니면_409(PaymentStatus status) {
        given(paymentRepository.findById(7L)).willReturn(Optional.of(payment(status)));

        assertInvalidStatus();
    }

    @Test
    void 결제가_없으면_404() {
        given(paymentRepository.findById(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.recover(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    void 토스_조회가_실패하면_결과를_정하지_않고_502() {
        given(paymentRepository.findById(7L)).willReturn(Optional.of(payment(PaymentStatus.CANCEL_REQUESTED)));
        given(tossCancelClient.getPayment("tgen_key", 7L))
                .willThrow(TossApiException.noResponse(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> service.recover(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_GATEWAY_ERROR));
        verifyNoInteractions(recorder, paymentCancelRepository);
    }

    private void assertInvalidStatus() {
        assertThatThrownBy(() -> service.recover(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_INVALID_STATUS));
        verifyNoInteractions(tossCancelClient, recorder);
    }

    // 처리 전 · 후 결제 (findById 를 두 번 읽음)
    private Payment stored(PaymentStatus before, PaymentStatus after) {
        Payment payment = payment(before);
        given(paymentRepository.findById(7L)).willReturn(Optional.of(payment), Optional.of(payment(after)));
        return payment;
    }

    private PaymentCancel latest(PaymentCancelStatus status) {
        PaymentCancel cancel = cancel(payment(PaymentStatus.CANCEL_REQUESTED), 3L, status);
        given(paymentCancelRepository.findTopByPaymentIdOrderByIdDesc(7L)).willReturn(Optional.of(cancel));
        return cancel;
    }

    private static TossCancelResponse toss(String status) {
        List<TossCancelResponse.Cancel> cancels = "CANCELED".equals(status)
                ? List.of(new TossCancelResponse.Cancel(18000, CANCELED_AT, "tx_cancel_1", "DONE"))
                : List.of();
        return new TossCancelResponse("tgen_key", "BDS-1005-1", status, 18000, cancels);
    }

    private static PaymentCancel cancel(Payment payment, Long id, PaymentCancelStatus status) {
        PaymentCancel cancel = PaymentCancel.fullCancel(payment, "재료 소진", CancelRequester.OWNER);
        ReflectionTestUtils.setField(cancel, "id", id);
        ReflectionTestUtils.setField(cancel, "status", status);
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
