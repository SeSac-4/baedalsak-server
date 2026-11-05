package com.baedalsak.payment.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossConfirmResponse;
import com.baedalsak.payment.client.toss.TossPaymentsClient;
import com.baedalsak.payment.client.toss.TossProperties;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.request.ConfirmPaymentRequest;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.error.PaymentConfirmRejectedException;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 결제 승인 (#14, #125). 검증 순서 · 토스 호출 전 키 저장 · 토스 에러 분류 · 다른 키 처리 · 시간 예산
 */
@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    static final LoginMember CUSTOMER = new LoginMember(1L, MemberRole.CUSTOMER);
    static final OffsetDateTime APPROVED_AT = OffsetDateTime.parse("2026-10-26T15:30:00+09:00");
    static final ConfirmPaymentRequest REQUEST = new ConfirmPaymentRequest("tgen_key", "BDS-1005-1", 18000);
    static final TossConfirmResponse TOSS = toss("tgen_key", "DONE", 18000);

    // 다른 키 경로: 이미 저장돼 있던 이전 키
    static final String PREVIOUS_KEY = "tgen_prev";
    static final ConfirmPaymentRequest PREVIOUS_REQUEST = new ConfirmPaymentRequest(PREVIOUS_KEY, "BDS-1005-1", 18000);
    static final TossConfirmResponse TOSS_PREVIOUS = toss(PREVIOUS_KEY, "DONE", 18000);

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    TossPaymentsClient tossPaymentsClient;
    @Mock
    PaymentApprover paymentApprover;
    @Mock
    PaymentKeyRecorder paymentKeyRecorder;
    PaymentConfirmService paymentConfirmService;

    // 연결 2초 · 읽기 5초(호출 1건 최대 7초), 결제 확인 예산 15초 (application.yml 과 같은 값)
    static final TossProperties TOSS_PROPERTIES = new TossProperties("http://toss", "test_sk",
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(15));
    final MutableClock clock = new MutableClock();

    @BeforeEach
    void setUp() {
        paymentConfirmService = new PaymentConfirmService(paymentRepository, tossPaymentsClient, paymentApprover,
                paymentKeyRecorder, TOSS_PROPERTIES, clock);
    }

    // ---------- 검증 (토스 호출 전) ----------

    @Test
    void 결제가_없으면_404_PAYMENT_NOT_FOUND() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.empty());

        assertErrorBeforeToss(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 남의_결제면_403_FORBIDDEN() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment(2L, 18000)));

        assertErrorBeforeToss(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 이미_승인됐으면_토스를_부르지_않고_409() {
        Payment payment = payment(1L, 18000);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.APPROVED);
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment));

        assertErrorBeforeToss(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
    }

    @Test
    void 금액이_다르면_400_PAYMENT_AMOUNT_MISMATCH() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment(1L, 15000)));

        assertErrorBeforeToss(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    // ---------- 키 저장 후 승인 ----------

    @Test
    void 토스_호출_전에_paymentKey를_저장하고_승인한다() {
        givenRequestedPayment();
        given(paymentKeyRecorder.reserve(7L, "tgen_key")).willReturn(true);
        given(tossPaymentsClient.confirm(REQUEST)).willReturn(TOSS);
        given(paymentApprover.approve(7L, TOSS)).willReturn(approvedPayment());

        PaymentDetailResponse response = paymentConfirmService.confirm(CUSTOMER, REQUEST);

        InOrder order = inOrder(paymentKeyRecorder, tossPaymentsClient, paymentApprover);
        order.verify(paymentKeyRecorder).reserve(7L, "tgen_key");
        order.verify(tossPaymentsClient).confirm(REQUEST);
        order.verify(paymentApprover).approve(7L, TOSS);
        assertThat(response.paymentId()).isEqualTo(7L);
        assertThat(response.orderId()).isEqualTo(1005L);
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.amount()).isEqualTo(18000);
        assertThat(response.method()).isEqualTo("카드");
        assertThat(response.approvedAt()).isEqualTo(APPROVED_AT);
        assertThat(response.receiptUrl()).isEqualTo("https://receipt/1");
        assertThat(response.approvalNumber()).isEqualTo("30024234");
    }

    @Test
    void 다른_결제에_이미_저장된_키면_400_토스_미호출() {
        givenRequestedPayment();
        given(paymentKeyRecorder.reserve(7L, "tgen_key"))
                .willThrow(new DataIntegrityViolationException("payment_toss_key_uq"));

        assertError(CommonErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(tossPaymentsClient, paymentApprover);
    }

    @Test
    void 키를_저장하지_못했는데_그사이_승인됐으면_409_토스_미호출() {
        givenRequestedPayment();
        given(paymentKeyRecorder.reserve(7L, "tgen_key")).willReturn(false);
        given(paymentRepository.findById(7L)).willReturn(Optional.of(approvedPayment()));

        assertError(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
        verifyNoInteractions(tossPaymentsClient, paymentApprover);
    }

    // ---------- 토스 에러 분류 ----------

    @Test
    void 결과를_모르면_같은_멱등_키로_1회_재전송해_승인한다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willThrow(noResponse()).willReturn(TOSS);
        given(paymentApprover.approve(7L, TOSS)).willReturn(approvedPayment());

        assertThat(paymentConfirmService.confirm(CUSTOMER, REQUEST).status()).isEqualTo(PaymentStatus.APPROVED);
        verify(tossPaymentsClient, times(2)).confirm(REQUEST);
    }

    @Test
    void 재전송해도_결과를_모르면_502_키는_남긴다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willThrow(noResponse()).willThrow(noResponse());

        assertError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(tossPaymentsClient, times(2)).confirm(REQUEST);
        verify(paymentKeyRecorder, never()).clear(anyLong(), anyString(), any());
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    @Test
    void PROVIDER_ERROR_와_5xx도_재전송_대상이다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST))
                .willThrow(TossApiException.rejected(400, "PROVIDER_ERROR", "일시적인 오류"))
                .willThrow(TossApiException.rejected(500, "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING", "내부 오류"));

        assertError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(tossPaymentsClient, times(2)).confirm(REQUEST);
        verify(paymentKeyRecorder, never()).clear(anyLong(), anyString(), any());
    }

    @Test
    void 확정_실패면_키를_비우고_400_토스_메시지를_전달한다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST))
                .willThrow(TossApiException.rejected(403, "REJECT_CARD_COMPANY", "카드사에서 승인을 거절했습니다."));

        assertThatThrownBy(() -> paymentConfirmService.confirm(CUSTOMER, REQUEST))
                .isInstanceOfSatisfying(PaymentConfirmRejectedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONFIRM_REJECTED);
                    assertThat(e.responseMessage()).isEqualTo("카드사에서 승인을 거절했습니다.");
                });
        // 확정 실패는 재전송하지 않는다
        verify(tossPaymentsClient, times(1)).confirm(REQUEST);
        verify(paymentKeyRecorder).clear(7L, "tgen_key", "REJECT_CARD_COMPANY");
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    @Test
    void CARD_PROCESSING_ERROR는_확정_실패다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST))
                .willThrow(TossApiException.rejected(400, "CARD_PROCESSING_ERROR", "카드사에서 오류가 발생했습니다."));

        assertThatThrownBy(() -> paymentConfirmService.confirm(CUSTOMER, REQUEST))
                .isInstanceOf(PaymentConfirmRejectedException.class);
        verify(paymentKeyRecorder).clear(7L, "tgen_key", "CARD_PROCESSING_ERROR");
    }

    @Test
    void ALREADY_PROCESSED_PAYMENT면_조회해서_승인_완료_일치면_승인한다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willThrow(alreadyProcessed());
        given(tossPaymentsClient.getPayment("tgen_key", "BDS-1005-1")).willReturn(TOSS);
        given(paymentApprover.approve(7L, TOSS)).willReturn(approvedPayment());

        assertThat(paymentConfirmService.confirm(CUSTOMER, REQUEST).status()).isEqualTo(PaymentStatus.APPROVED);
        verify(tossPaymentsClient, times(1)).confirm(REQUEST);
    }

    @Test
    void ALREADY_PROCESSED_PAYMENT인데_조회_금액이_다르면_502_키는_남긴다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willThrow(alreadyProcessed());
        given(tossPaymentsClient.getPayment("tgen_key", "BDS-1005-1")).willReturn(toss("tgen_key", "DONE", 15000));

        assertError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(paymentApprover, never()).approve(anyLong(), any());
        verify(paymentKeyRecorder, never()).clear(anyLong(), anyString(), any());
    }

    @Test
    void ALREADY_PROCESSED_PAYMENT인데_조회도_실패하면_502() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willThrow(alreadyProcessed());
        given(tossPaymentsClient.getPayment("tgen_key", "BDS-1005-1")).willThrow(noResponse());

        assertError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    @Test
    void 승인_응답이_DONE이_아니면_APPROVED로_저장하지_않고_502() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willReturn(toss("tgen_key", "WAITING_FOR_DEPOSIT", 18000));

        assertError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(paymentApprover, never()).approve(anyLong(), any());
        verify(paymentKeyRecorder, never()).clear(anyLong(), anyString(), any());
    }

    // ---------- 다른 키(이전 키 A 저장됨, 새 키 B 요청) ----------

    @Test
    void 이전_키_재전송이_승인이면_이전_키로_기록하고_409_새_키는_승인_안_함() {
        givenPreviousKeyStored();
        given(tossPaymentsClient.confirm(PREVIOUS_REQUEST)).willReturn(TOSS_PREVIOUS);

        assertError(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
        verify(paymentApprover).approve(7L, TOSS_PREVIOUS);
        verify(tossPaymentsClient, never()).confirm(REQUEST);
    }

    @Test
    void 이전_키가_확정_실패면_비우고_새_키로_승인한다() {
        givenPreviousKeyStored();
        given(tossPaymentsClient.confirm(PREVIOUS_REQUEST))
                .willThrow(TossApiException.rejected(404, "NOT_FOUND_PAYMENT_SESSION", "결제 시간이 만료되었습니다."));
        given(paymentKeyRecorder.clear(7L, PREVIOUS_KEY, "NOT_FOUND_PAYMENT_SESSION")).willReturn(true);
        given(tossPaymentsClient.confirm(REQUEST)).willReturn(TOSS);
        given(paymentApprover.approve(7L, TOSS)).willReturn(approvedPayment());

        assertThat(paymentConfirmService.confirm(CUSTOMER, REQUEST).status()).isEqualTo(PaymentStatus.APPROVED);

        InOrder order = inOrder(paymentKeyRecorder, tossPaymentsClient);
        order.verify(tossPaymentsClient).confirm(PREVIOUS_REQUEST);
        order.verify(paymentKeyRecorder).clear(7L, PREVIOUS_KEY, "NOT_FOUND_PAYMENT_SESSION");
        order.verify(paymentKeyRecorder).reserve(7L, "tgen_key");
        order.verify(tossPaymentsClient).confirm(REQUEST);
    }

    @Test
    void 이전_키_결과를_모르면_1번만_보내고_409_PAYMENT_KEY_MISMATCH() {
        givenPreviousKeyStored();
        given(tossPaymentsClient.confirm(PREVIOUS_REQUEST)).willThrow(noResponse());

        assertError(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
        verify(tossPaymentsClient, times(1)).confirm(PREVIOUS_REQUEST);
        verify(tossPaymentsClient, never()).confirm(REQUEST);
        verify(paymentKeyRecorder, never()).clear(anyLong(), anyString(), any());
    }

    @Test
    void 이전_키가_ALREADY_PROCESSED면_조회_일치_시_이전_키로_기록하고_409() {
        givenPreviousKeyStored();
        given(tossPaymentsClient.confirm(PREVIOUS_REQUEST)).willThrow(alreadyProcessed());
        given(tossPaymentsClient.getPayment(PREVIOUS_KEY, "BDS-1005-1")).willReturn(TOSS_PREVIOUS);

        assertError(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
        verify(paymentApprover).approve(7L, TOSS_PREVIOUS);
        verify(tossPaymentsClient, never()).confirm(REQUEST);
    }

    @Test
    void 이전_키가_ALREADY_PROCESSED인데_조회가_일치하지_않으면_409_PAYMENT_KEY_MISMATCH() {
        givenPreviousKeyStored();
        given(tossPaymentsClient.confirm(PREVIOUS_REQUEST)).willThrow(alreadyProcessed());
        given(tossPaymentsClient.getPayment(PREVIOUS_KEY, "BDS-1005-1")).willThrow(noResponse());

        assertError(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    // ---------- 시간 예산 (15초, 토스 호출 전 남은 시간이 7초 미만이면 부르지 않음) ----------

    @Test
    void 첫_승인에_9초가_걸려_예산이_7초_미만이면_재전송하지_않고_502_키는_남긴다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willAnswer(inv -> {
            clock.advance(Duration.ofSeconds(9));
            throw noResponse();
        });

        assertError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(tossPaymentsClient, times(1)).confirm(REQUEST);
        verify(paymentKeyRecorder, never()).clear(anyLong(), anyString(), any());
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    @Test
    void 첫_승인에_8초가_걸려도_7초가_남으면_재전송한다() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willAnswer(inv -> {
            clock.advance(Duration.ofSeconds(8));
            throw noResponse();
        }).willReturn(TOSS);
        given(paymentApprover.approve(7L, TOSS)).willReturn(approvedPayment());

        assertThat(paymentConfirmService.confirm(CUSTOMER, REQUEST).status()).isEqualTo(PaymentStatus.APPROVED);
        verify(tossPaymentsClient, times(2)).confirm(REQUEST);
    }

    @Test
    void ALREADY_PROCESSED인데_예산이_없으면_조회하지_않고_502() {
        givenReservedPayment();
        given(tossPaymentsClient.confirm(REQUEST)).willAnswer(inv -> {
            clock.advance(Duration.ofSeconds(9));
            throw alreadyProcessed();
        });

        assertError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(tossPaymentsClient, never()).getPayment(anyString(), anyString());
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    @Test
    void 이전_키가_확정_실패여도_예산이_없으면_새_키_승인을_부르지_않고_502() {
        givenPreviousKeyStored();
        given(tossPaymentsClient.confirm(PREVIOUS_REQUEST)).willAnswer(inv -> {
            clock.advance(Duration.ofSeconds(9));
            throw TossApiException.rejected(404, "NOT_FOUND_PAYMENT_SESSION", "결제 시간이 만료되었습니다.");
        });
        given(paymentKeyRecorder.clear(7L, PREVIOUS_KEY, "NOT_FOUND_PAYMENT_SESSION")).willReturn(true);

        assertError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(tossPaymentsClient, never()).confirm(REQUEST);
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    // ---------- 준비 ----------

    private void givenRequestedPayment() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment(1L, 18000)));
    }

    private void givenReservedPayment() {
        givenRequestedPayment();
        given(paymentKeyRecorder.reserve(7L, "tgen_key")).willReturn(true);
    }

    // 이전 키가 저장돼 있어 새 키 저장이 0행 → 다시 읽으면 이전 키가 보인다. 이전 키를 비운 뒤 새 키 저장은 성공
    private void givenPreviousKeyStored() {
        givenRequestedPayment();
        given(paymentKeyRecorder.reserve(7L, "tgen_key")).willReturn(false).willReturn(true);
        Payment current = payment(1L, 18000);
        ReflectionTestUtils.setField(current, "tossPaymentKey", PREVIOUS_KEY);
        given(paymentRepository.findById(7L)).willReturn(Optional.of(current));
    }

    // 토스 호출 전에 막히는 검증: 키 저장 · 토스 · 승인 저장 모두 하지 않는다
    private void assertErrorBeforeToss(ErrorCode expected) {
        assertError(expected);
        verifyNoInteractions(paymentKeyRecorder, tossPaymentsClient, paymentApprover);
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> paymentConfirmService.confirm(CUSTOMER, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private static TossApiException noResponse() {
        return TossApiException.noResponse(new SocketTimeoutException("Read timed out"));
    }

    private static TossApiException alreadyProcessed() {
        return TossApiException.rejected(400, "ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다.");
    }

    private static TossConfirmResponse toss(String paymentKey, String status, int totalAmount) {
        return new TossConfirmResponse(paymentKey, "BDS-1005-1", status, totalAmount, "카드", APPROVED_AT,
                new TossConfirmResponse.Receipt("https://receipt/1"), new TossConfirmResponse.Card("30024234"));
    }

    private static Payment payment(Long customerId, int amount) {
        Payment payment = Payment.request(1005L, customerId, 10L, 3L, "싹분식", "customer@test.com",
                "owner@test.com", "BDS-1005-1", amount);
        ReflectionTestUtils.setField(payment, "id", 7L);
        return payment;
    }

    private static Payment approvedPayment() {
        Payment approved = payment(1L, 18000);
        ReflectionTestUtils.setField(approved, "status", PaymentStatus.APPROVED);
        ReflectionTestUtils.setField(approved, "tossPaymentKey", "tgen_key");
        ReflectionTestUtils.setField(approved, "method", "카드");
        ReflectionTestUtils.setField(approved, "approvedAt", APPROVED_AT);
        ReflectionTestUtils.setField(approved, "receiptUrl", "https://receipt/1");
        ReflectionTestUtils.setField(approved, "approvalNumber", "30024234");
        return approved;
    }

    // 테스트에서 시간을 앞으로 보낼 수 있는 Clock
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
