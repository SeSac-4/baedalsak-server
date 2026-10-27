package com.baedalsak.payment.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.payment.client.toss.TossConfirmResponse;
import com.baedalsak.payment.client.toss.TossPaymentsClient;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.request.ConfirmPaymentRequest;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
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
 * 결제 승인 (#14). 검증 순서·토스 호출 여부
 */
@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    static final LoginMember CUSTOMER = new LoginMember(1L, MemberRole.CUSTOMER);
    static final ConfirmPaymentRequest REQUEST = new ConfirmPaymentRequest("tgen_key", "BDS-1005-1", 18000);
    static final TossConfirmResponse TOSS = new TossConfirmResponse("tgen_key", "BDS-1005-1", "카드",
            OffsetDateTime.parse("2026-10-26T15:30:00+09:00"), new TossConfirmResponse.Receipt("https://receipt/1"));

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    TossPaymentsClient tossPaymentsClient;
    @Mock
    PaymentApprover paymentApprover;
    @InjectMocks
    PaymentConfirmService paymentConfirmService;

    @Test
    void 검증을_통과하면_토스_승인_후_결제_상세를_돌려준다() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment(1L, 18000)));
        given(tossPaymentsClient.confirm(REQUEST)).willReturn(TOSS);
        Payment approved = payment(1L, 18000);
        ReflectionTestUtils.setField(approved, "status", PaymentStatus.APPROVED);
        ReflectionTestUtils.setField(approved, "method", "카드");
        ReflectionTestUtils.setField(approved, "approvedAt", TOSS.approvedAt());
        ReflectionTestUtils.setField(approved, "receiptUrl", "https://receipt/1");
        given(paymentApprover.approve(7L, TOSS)).willReturn(approved);

        PaymentDetailResponse response = paymentConfirmService.confirm(CUSTOMER, REQUEST);

        assertThat(response.paymentId()).isEqualTo(7L);
        assertThat(response.orderId()).isEqualTo(1005L);
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.amount()).isEqualTo(18000);
        assertThat(response.method()).isEqualTo("카드");
        assertThat(response.approvedAt()).isEqualTo(TOSS.approvedAt());
        assertThat(response.receiptUrl()).isEqualTo("https://receipt/1");
    }

    @Test
    void 결제가_없으면_404_PAYMENT_NOT_FOUND() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.empty());

        assertError(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 남의_결제면_403_FORBIDDEN() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment(2L, 18000)));

        assertError(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 이미_승인됐으면_토스를_부르지_않고_409() {
        Payment payment = payment(1L, 18000);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.APPROVED);
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment));

        assertError(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
    }

    @Test
    void 금액이_다르면_400_PAYMENT_AMOUNT_MISMATCH() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment(1L, 15000)));

        assertError(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    void 토스_승인이_실패하면_502_저장하지_않음() {
        given(paymentRepository.findByTossOrderId("BDS-1005-1")).willReturn(Optional.of(payment(1L, 18000)));
        given(tossPaymentsClient.confirm(REQUEST))
                .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR));

        assertThatThrownBy(() -> paymentConfirmService.confirm(CUSTOMER, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        verify(paymentApprover, never()).approve(anyLong(), any());
    }

    // 토스 호출 전에 막히는 검증: 토스도 저장도 하지 않는다
    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> paymentConfirmService.confirm(CUSTOMER, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
        verifyNoInteractions(tossPaymentsClient, paymentApprover);
    }

    private static Payment payment(Long customerId, int amount) {
        Payment payment = Payment.request(1005L, customerId, 10L, 3L, "싹분식", "customer@test.com",
                "owner@test.com", "BDS-1005-1", amount);
        ReflectionTestUtils.setField(payment, "id", 7L);
        return payment;
    }
}
