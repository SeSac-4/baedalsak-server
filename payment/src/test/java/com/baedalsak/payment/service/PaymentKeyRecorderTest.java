package com.baedalsak.payment.service;

import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 토스 승인 전 paymentKey 저장 · 확정 실패 키 비우기 (#125). 조건부 갱신 결과로 성공 여부를 판단
 */
@ExtendWith(MockitoExtension.class)
class PaymentKeyRecorderTest {

    @Mock
    PaymentRepository paymentRepository;
    @InjectMocks
    PaymentKeyRecorder paymentKeyRecorder;

    @Test
    void 결제요청됨이고_키가_비었거나_같으면_저장한다() {
        given(paymentRepository.reservePaymentKey(7L, "tgen_key", PaymentStatus.REQUESTED)).willReturn(1);

        assertThat(paymentKeyRecorder.reserve(7L, "tgen_key")).isTrue();
    }

    @Test
    void 다른_키가_있거나_결제요청됨이_아니면_저장하지_않는다() {
        given(paymentRepository.reservePaymentKey(7L, "tgen_key", PaymentStatus.REQUESTED)).willReturn(0);

        assertThat(paymentKeyRecorder.reserve(7L, "tgen_key")).isFalse();
    }

    @Test
    void 저장된_키가_같으면_비운다() {
        given(paymentRepository.clearPaymentKey(7L, "tgen_key", PaymentStatus.REQUESTED)).willReturn(1);

        assertThat(paymentKeyRecorder.clear(7L, "tgen_key", "REJECT_CARD_COMPANY")).isTrue();
    }

    @Test
    void 그사이_키가_바뀌었으면_비우지_않는다() {
        given(paymentRepository.clearPaymentKey(7L, "tgen_key", PaymentStatus.REQUESTED)).willReturn(0);

        assertThat(paymentKeyRecorder.clear(7L, "tgen_key", "REJECT_CARD_COMPANY")).isFalse();
    }
}
