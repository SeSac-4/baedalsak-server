package com.baedalsak.payment.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossConfirmResponse;
import com.baedalsak.payment.client.toss.TossPaymentsClient;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.request.InternalCreatePaymentRequest;
import com.baedalsak.payment.dto.response.ExpireResultResponse;
import com.baedalsak.payment.dto.response.ExpireResultResponse.Result;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.dto.response.PaymentStatusResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 * payment 내부 API (#128). 결제 요청 생성 · 상태 일괄 조회 · 만료(키 없음 / 토스 조회)
 */
@ExtendWith(MockitoExtension.class)
class PaymentInternalServiceTest {

    static final OffsetDateTime APPROVED_AT = OffsetDateTime.parse("2026-11-05T12:00:00+09:00");

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentCreator paymentCreator;
    @Mock
    PaymentExpirer paymentExpirer;
    @Mock
    PaymentApprover paymentApprover;
    @Mock
    TossPaymentsClient tossPaymentsClient;
    @InjectMocks
    PaymentInternalService paymentInternalService;

    // ---------- 결제 요청 생성 ----------

    @Test
    void 결제_요청을_만들고_결제창_값을_돌려준다() {
        given(paymentCreator.create(any())).willReturn(payment(PaymentStatus.REQUESTED, null));

        PaymentReadyResponse response = paymentInternalService.create(createRequest());

        assertThat(response.paymentId()).isEqualTo(7L);
        assertThat(response.tossOrderId()).isEqualTo("BDS-1005-1");
        assertThat(response.orderName()).isEqualTo("참치김밥 외 1건");
        assertThat(response.amount()).isEqualTo(18000);
    }

    @Test
    void 같은_주문의_결제가_만료됐으면_409_ORDER_INVALID_STATUS() {
        given(paymentCreator.create(any())).willReturn(payment(PaymentStatus.EXPIRED, null));

        assertThatThrownBy(() -> paymentInternalService.create(createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.ORDER_INVALID_STATUS);
    }

    // ---------- 상태 일괄 조회 ----------

    @Test
    void 상태_조회는_주문_ID_결제_ID_상태_금액_승인_시각을_돌려준다() {
        Payment approved = payment(PaymentStatus.APPROVED, "tgen_key");
        ReflectionTestUtils.setField(approved, "approvedAt", APPROVED_AT);
        given(paymentRepository.findByOrderIdIn(List.of(1005L, 1006L))).willReturn(List.of(approved));

        List<PaymentStatusResponse> statuses = paymentInternalService.findStatuses(List.of(1005L, 1006L));

        assertThat(statuses).containsExactly(
                new PaymentStatusResponse(1005L, 7L, PaymentStatus.APPROVED, 18000, APPROVED_AT));
    }

    // ---------- 만료 ----------

    @Test
    void 결제가_없으면_NONE() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty());

        assertThat(expire()).isEqualTo(ExpireResultResponse.none(1005L));
    }

    @Test
    void 이미_승인이면_APPROVED_와_승인_시각() {
        Payment approved = payment(PaymentStatus.APPROVED, "tgen_key");
        ReflectionTestUtils.setField(approved, "approvedAt", APPROVED_AT);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(approved));

        assertThat(expire()).isEqualTo(ExpireResultResponse.approved(1005L, APPROVED_AT));
        verifyNoInteractions(tossPaymentsClient, paymentExpirer);
    }

    @Test
    void 이미_만료면_EXPIRED() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(payment(PaymentStatus.EXPIRED, null)));

        assertThat(expire().result()).isEqualTo(Result.EXPIRED);
        verifyNoInteractions(tossPaymentsClient, paymentExpirer);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = PaymentStatus.class, names = {"CANCEL_REQUESTED", "CANCELED"})
    void 환불_처리_중_환불_완료도_승인됐던_결제라_APPROVED_와_승인_시각(PaymentStatus status) {
        // #127: 반복 호출 없이 바로 응답 (조정자가 다시 명령을 보내도 payment 는 결제 상태로 무시)
        Payment refunded = payment(status, "tgen_key");
        ReflectionTestUtils.setField(refunded, "approvedAt", APPROVED_AT);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(refunded));

        assertThat(expire()).isEqualTo(ExpireResultResponse.approved(1005L, APPROVED_AT));
        verifyNoInteractions(tossPaymentsClient, paymentExpirer);
    }

    @Test
    void 키_없음이면_토스_조회_없이_만료() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(payment(PaymentStatus.REQUESTED, null)));
        given(paymentExpirer.expireIfKeyless(7L)).willReturn(true);

        assertThat(expire().result()).isEqualTo(Result.EXPIRED);
        verifyNoInteractions(tossPaymentsClient);
    }

    @Test
    void 키가_있고_토스가_승인_완료가_아니면_만료() {
        Payment keyed = payment(PaymentStatus.REQUESTED, "tgen_key");
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(keyed));
        given(paymentRepository.findById(7L)).willReturn(Optional.of(keyed));
        given(tossPaymentsClient.getPayment("tgen_key", "BDS-1005-1")).willReturn(toss("IN_PROGRESS", 18000));
        given(paymentExpirer.expireWithKey(7L, "tgen_key")).willReturn(true);

        assertThat(expire().result()).isEqualTo(Result.EXPIRED);
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    @Test
    void 키가_있고_토스가_승인_완료면_승인으로_기록하고_APPROVED() {
        Payment keyed = payment(PaymentStatus.REQUESTED, "tgen_key");
        TossConfirmResponse done = toss("DONE", 18000);
        Payment approved = payment(PaymentStatus.APPROVED, "tgen_key");
        ReflectionTestUtils.setField(approved, "approvedAt", APPROVED_AT);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(keyed));
        given(paymentRepository.findById(7L)).willReturn(Optional.of(keyed));
        given(tossPaymentsClient.getPayment("tgen_key", "BDS-1005-1")).willReturn(done);
        given(paymentApprover.approve(7L, done)).willReturn(approved);

        assertThat(expire()).isEqualTo(ExpireResultResponse.approved(1005L, APPROVED_AT));
        verify(paymentExpirer, never()).expireWithKey(anyLong(), anyString());
    }

    @Test
    void 토스_조회가_실패하면_결과를_정하지_않고_502() {
        Payment keyed = payment(PaymentStatus.REQUESTED, "tgen_key");
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(keyed));
        given(paymentRepository.findById(7L)).willReturn(Optional.of(keyed));
        given(tossPaymentsClient.getPayment("tgen_key", "BDS-1005-1"))
                .willThrow(TossApiException.noResponse(new SocketTimeoutException("Read timed out")));

        assertExpireError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(paymentExpirer, never()).expireWithKey(anyLong(), anyString());
    }

    @Test
    void 토스_승인_금액이_다르면_기록하지_않고_502() {
        Payment keyed = payment(PaymentStatus.REQUESTED, "tgen_key");
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(keyed));
        given(paymentRepository.findById(7L)).willReturn(Optional.of(keyed));
        given(tossPaymentsClient.getPayment("tgen_key", "BDS-1005-1")).willReturn(toss("DONE", 9000));

        assertExpireError(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    private ExpireResultResponse expire() {
        List<ExpireResultResponse> results = paymentInternalService.expire(List.of(1005L));
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    private void assertExpireError(PaymentErrorCode expected) {
        assertThatThrownBy(() -> paymentInternalService.expire(List.of(1005L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private static InternalCreatePaymentRequest createRequest() {
        return new InternalCreatePaymentRequest(1005L, 18000, 1L, 10L, 3L, "싹분식",
                "customer@test.com", "owner@test.com", "참치김밥 외 1건");
    }

    private static Payment payment(PaymentStatus status, String paymentKey) {
        Payment payment = Payment.request(1005L, 1L, 10L, 3L, "싹분식", "customer@test.com", "owner@test.com",
                "BDS-1005-1", 18000);
        ReflectionTestUtils.setField(payment, "id", 7L);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "tossPaymentKey", paymentKey);
        return payment;
    }

    private static TossConfirmResponse toss(String status, int totalAmount) {
        return new TossConfirmResponse("tgen_key", "BDS-1005-1", status, totalAmount, "카드", APPROVED_AT, null, null);
    }
}
