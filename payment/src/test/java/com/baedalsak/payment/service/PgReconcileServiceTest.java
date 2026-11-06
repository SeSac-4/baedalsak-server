package com.baedalsak.payment.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossTransaction;
import com.baedalsak.payment.client.toss.TossTransactionClient;
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
import com.baedalsak.payment.service.PgReconcileService.Outcome;
import com.baedalsak.payment.service.PgReconcileService.Summary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * PG 대사 (#154). 토스 거래와 결제 상태 비교 → 일치 · 자동 보정(환불 처리 중, 재처리 서비스) · 처리 중 · 불일치(알림).
 * 비교 대상은 거래의 결제 키 + 기간 안에 승인된 결제. 처리 규칙은 사가 대사 노트 10절
 */
@ExtendWith(MockitoExtension.class)
class PgReconcileServiceTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 11, 6, 0, 0);
    static final LocalDateTime END = LocalDateTime.of(2026, 11, 6, 17, 30);
    static final OffsetDateTime FROM = OffsetDateTime.parse("2026-11-06T00:00:00+09:00");
    static final OffsetDateTime TO = OffsetDateTime.parse("2026-11-06T17:30:00+09:00");

    @Mock
    TossTransactionClient tossTransactionClient;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentCancelRepository paymentCancelRepository;
    @Mock
    PaymentRecoverService paymentRecoverService;
    @InjectMocks
    PgReconcileService service;

    // ---------- 일치 ----------

    @Test
    void 승인_거래와_승인_완료_결제는_일치() {
        Summary summary = reconcile(List.of(done("pk_1", 18000)), payment(7L, "pk_1", PaymentStatus.APPROVED));

        assertThat(summary.compared()).isEqualTo(1);
        assertThat(summary.count(Outcome.MATCHED)).isEqualTo(1);
        verifyNoInteractions(paymentRecoverService);
    }

    @Test
    void 승인과_취소_거래가_있고_환불_완료_결제면_일치() {
        Summary summary = reconcile(List.of(done("pk_1", 18000), canceled("pk_1")),
                payment(7L, "pk_1", PaymentStatus.CANCELED));

        assertThat(summary.count(Outcome.MATCHED)).isEqualTo(1);
    }

    @Test
    void 이전에_승인돼_오늘_취소_거래만_있는_환불_완료_결제도_일치() {
        Summary summary = reconcile(List.of(canceled("pk_1")), payment(7L, "pk_1", PaymentStatus.CANCELED));

        assertThat(summary.count(Outcome.MATCHED)).isEqualTo(1);
    }

    // ---------- 자동 보정 (환불 처리 중 → 재처리 서비스) ----------

    @Test
    void 환불_처리_중인데_토스_취소면_재처리로_완료_기록() {
        Payment payment = payment(7L, "pk_1", PaymentStatus.CANCEL_REQUESTED);
        latestCancel(payment, PaymentCancelStatus.PENDING);
        given(paymentRecoverService.recover(7L)).willReturn(recovered(Action.RECORDED_CANCELED));

        Summary summary = reconcile(List.of(done("pk_1", 18000), canceled("pk_1")), payment);

        assertThat(summary.count(Outcome.FIXED)).isEqualTo(1);
    }

    @Test
    void 환불_처리_중_진행_중인데_토스_승인이면_재처리로_다시_취소() {
        Payment payment = payment(7L, "pk_1", PaymentStatus.CANCEL_REQUESTED);
        latestCancel(payment, PaymentCancelStatus.PENDING);
        given(paymentRecoverService.recover(7L)).willReturn(recovered(Action.CANCELED));

        assertThat(reconcile(List.of(done("pk_1", 18000)), payment).count(Outcome.FIXED)).isEqualTo(1);
    }

    @Test
    void 확정_거절_뒤_토스가_실제로_취소면_재처리로_완료_기록() {
        Payment payment = payment(7L, "pk_1", PaymentStatus.CANCEL_REQUESTED);
        latestCancel(payment, PaymentCancelStatus.FAILED);
        given(paymentRecoverService.recover(7L)).willReturn(recovered(Action.RECORDED_CANCELED));

        assertThat(reconcile(List.of(done("pk_1", 18000), canceled("pk_1")), payment).count(Outcome.FIXED))
                .isEqualTo(1);
    }

    @Test
    void 같은_취소를_다른_경로가_처리_중이면_건너뛴다() {
        Payment payment = payment(7L, "pk_1", PaymentStatus.CANCEL_REQUESTED);
        latestCancel(payment, PaymentCancelStatus.PENDING);
        given(paymentRecoverService.recover(7L)).willReturn(recovered(Action.IN_PROGRESS));

        Summary summary = reconcile(List.of(done("pk_1", 18000)), payment);

        assertThat(summary.count(Outcome.IN_PROGRESS)).isEqualTo(1);
        assertThat(summary.count(Outcome.MISMATCH)).isZero();
    }

    // ---------- 불일치 (알림) ----------

    @Test
    void 확정_거절_뒤_토스_승인이면_새_키_취소라_재처리를_부르지_않고_알림() {
        Payment payment = payment(7L, "pk_1", PaymentStatus.CANCEL_REQUESTED);
        latestCancel(payment, PaymentCancelStatus.FAILED);

        assertThat(reconcile(List.of(done("pk_1", 18000)), payment).count(Outcome.MISMATCH)).isEqualTo(1);
        verify(paymentRecoverService, never()).recover(anyLong());
    }

    @Test
    void 재처리_결과를_모르거나_재처리가_실패하면_알림() {
        Payment unknown = payment(7L, "pk_1", PaymentStatus.CANCEL_REQUESTED);
        Payment failed = payment(8L, "pk_2", PaymentStatus.CANCEL_REQUESTED);
        latestCancel(unknown, PaymentCancelStatus.PENDING);
        latestCancel(failed, PaymentCancelStatus.PENDING);
        given(paymentRecoverService.recover(7L)).willReturn(recovered(Action.UNKNOWN));
        given(paymentRecoverService.recover(8L)).willThrow(new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR));

        Summary summary = reconcile(List.of(done("pk_1", 18000), done("pk_2", 18000)), unknown, failed);

        assertThat(summary.count(Outcome.MISMATCH)).isEqualTo(2);
    }

    @Test
    void 승인_완료인데_토스_취소면_알림() {
        Summary summary = reconcile(List.of(done("pk_1", 18000), canceled("pk_1")),
                payment(7L, "pk_1", PaymentStatus.APPROVED));

        assertThat(summary.count(Outcome.MISMATCH)).isEqualTo(1);
        verifyNoInteractions(paymentRecoverService);
    }

    @Test
    void 승인_완료인데_금액이_다르면_알림() {
        assertThat(reconcile(List.of(done("pk_1", 1000)), payment(7L, "pk_1", PaymentStatus.APPROVED))
                .count(Outcome.MISMATCH)).isEqualTo(1);
    }

    @Test
    void 기간_안에_승인됐는데_토스_거래가_없으면_알림() {
        Payment approved = payment(7L, "pk_1", PaymentStatus.APPROVED);
        given(tossTransactionClient.getTransactions(START, END)).willReturn(List.of());
        given(paymentRepository.findByTossPaymentKeyIn(any())).willReturn(List.of());
        given(paymentRepository.findByApprovedAtBetween(FROM, TO)).willReturn(List.of(approved));

        Summary summary = service.reconcile(START, END);

        assertThat(summary.compared()).isEqualTo(1);
        assertThat(summary.count(Outcome.MISMATCH)).isEqualTo(1);
    }

    @Test
    void 결제_요청됨인데_토스_승인이면_알림() {
        assertThat(reconcile(List.of(done("pk_1", 18000)), payment(7L, "pk_1", PaymentStatus.REQUESTED))
                .count(Outcome.MISMATCH)).isEqualTo(1);
    }

    @Test
    void 결제만료인데_토스_승인이면_알림_토스_취소면_일치() {
        Summary summary = reconcile(List.of(done("pk_1", 18000), done("pk_2", 18000), canceled("pk_2")),
                payment(7L, "pk_1", PaymentStatus.EXPIRED), payment(8L, "pk_2", PaymentStatus.EXPIRED));

        assertThat(summary.count(Outcome.MISMATCH)).isEqualTo(1);
        assertThat(summary.count(Outcome.MATCHED)).isEqualTo(1);
    }

    @Test
    void 토스_거래가_DB_에_없는_결제면_알림() {
        assertThat(reconcile(List.of(done("pk_unknown", 18000))).count(Outcome.MISMATCH)).isEqualTo(1);
    }

    // ---------- 거래 조회 실패 ----------

    @Test
    void 거래_조회가_실패하면_예외를_던지고_아무것도_바꾸지_않는다() {
        given(tossTransactionClient.getTransactions(START, END))
                .willThrow(TossApiException.noResponse(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> service.reconcile(START, END)).isInstanceOf(TossApiException.class);
        verifyNoInteractions(paymentRepository, paymentRecoverService);
    }

    private Summary reconcile(List<TossTransaction> transactions, Payment... payments) {
        given(tossTransactionClient.getTransactions(START, END)).willReturn(transactions);
        given(paymentRepository.findByTossPaymentKeyIn(any())).willReturn(List.of(payments));
        given(paymentRepository.findByApprovedAtBetween(FROM, TO)).willReturn(List.of());
        return service.reconcile(START, END);
    }

    private void latestCancel(Payment payment, PaymentCancelStatus status) {
        PaymentCancel cancel = PaymentCancel.fullCancel(payment, "재료 소진", CancelRequester.OWNER);
        ReflectionTestUtils.setField(cancel, "status", status);
        given(paymentCancelRepository.findTopByPaymentIdOrderByIdDesc(payment.getId())).willReturn(Optional.of(cancel));
    }

    private static RecoverResultResponse recovered(Action action) {
        return new RecoverResultResponse(7L, 1005L, PaymentStatus.CANCEL_REQUESTED, "CANCELED", action,
                PaymentStatus.CANCELED);
    }

    private static TossTransaction done(String paymentKey, int amount) {
        return new TossTransaction("tx_" + paymentKey + "_done", paymentKey, "BDS-1005-1", "DONE", amount,
                OffsetDateTime.parse("2026-11-06T12:00:00+09:00"));
    }

    private static TossTransaction canceled(String paymentKey) {
        return new TossTransaction("tx_" + paymentKey + "_cancel", paymentKey, "BDS-1005-1", "CANCELED", 18000,
                OffsetDateTime.parse("2026-11-06T13:00:00+09:00"));
    }

    private static Payment payment(Long id, String paymentKey, PaymentStatus status) {
        Payment payment = Payment.request(1005L, 1L, 10L, 3L, "싹분식", "customer@test.com", "owner@test.com",
                "BDS-1005-1", 18000);
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "tossPaymentKey", paymentKey);
        return payment;
    }
}
