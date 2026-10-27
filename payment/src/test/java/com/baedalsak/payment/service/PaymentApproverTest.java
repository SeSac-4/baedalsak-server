package com.baedalsak.payment.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.client.toss.TossConfirmResponse;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.event.OutboxAppender;
import com.baedalsak.payment.event.PaymentApprovedEvent;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 승인 저장 (#14). 조건부 갱신 결과에 따라 outbox 저장 여부가 갈린다
 */
@ExtendWith(MockitoExtension.class)
class PaymentApproverTest {

    static final OffsetDateTime APPROVED_AT = OffsetDateTime.parse("2026-10-26T15:30:00+09:00");
    static final TossConfirmResponse TOSS = new TossConfirmResponse("tgen_key", "BDS-1005-1", "카드",
            APPROVED_AT, new TossConfirmResponse.Receipt("https://receipt/1"));

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    OutboxAppender outboxAppender;
    @InjectMocks
    PaymentApprover paymentApprover;

    @Test
    void 결제요청됨이면_승인으로_바꾸고_PaymentApproved_outbox를_저장한다() {
        given(paymentRepository.approve(7L, "tgen_key", "카드", APPROVED_AT, "https://receipt/1",
                PaymentStatus.REQUESTED, PaymentStatus.APPROVED)).willReturn(1);
        given(paymentRepository.findById(7L)).willReturn(Optional.of(approvedPayment()));

        Payment payment = paymentApprover.approve(7L, TOSS);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxAppender).append(eq("PaymentApproved"), eq("payment.approved"), eq(7L), payload.capture());
        // 페이로드 필드는 설계서 '이벤트 목록' 탭, 가게·수신자 값은 결제 행 스냅샷
        assertThat(payload.getValue()).isEqualTo(new PaymentApprovedEvent(7L, 1005L, 1L, 10L, 3L, "싹분식",
                18000, APPROVED_AT, "customer@test.com", "owner@test.com"));
    }

    @Test
    void 이미_승인된_결제면_409_outbox_저장_안_함() {
        given(paymentRepository.approve(anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
                .willReturn(0);

        assertThatThrownBy(() -> paymentApprover.approve(7L, TOSS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
        verifyNoInteractions(outboxAppender);
    }

    private static Payment approvedPayment() {
        Payment payment = Payment.request(1005L, 1L, 10L, 3L, "싹분식", "customer@test.com", "owner@test.com",
                "BDS-1005-1", 18000);
        ReflectionTestUtils.setField(payment, "id", 7L);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.APPROVED);
        ReflectionTestUtils.setField(payment, "tossPaymentKey", "tgen_key");
        ReflectionTestUtils.setField(payment, "approvedAt", APPROVED_AT);
        return payment;
    }
}
