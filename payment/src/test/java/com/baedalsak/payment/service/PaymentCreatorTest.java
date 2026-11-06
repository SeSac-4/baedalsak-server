package com.baedalsak.payment.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 결제 요청 생성 (#128). 주문당 결제 1건(멱등) · 고객당 진행 중 결제 요청 1건(두 탭 이중 결제 방지)
 */
@ExtendWith(MockitoExtension.class)
class PaymentCreatorTest {

    static final PaymentDraft DRAFT = new PaymentDraft(1005L, 18000, 1L, 10L, 3L, "싹분식",
            "customer@test.com", "owner@test.com");

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentExpirer paymentExpirer;
    @InjectMocks
    PaymentCreator paymentCreator;

    @Test
    void 진행_중_결제가_없으면_새로_만든다() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty());
        given(paymentRepository.findByCustomerIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 7L));

        Payment payment = paymentCreator.create(DRAFT);

        assertThat(payment.getId()).isEqualTo(7L);
        assertThat(payment.getOrderId()).isEqualTo(1005L);
        assertThat(payment.getTossOrderId()).matches("BDS-1005-\\d+");
        verify(paymentExpirer, never()).expireIfKeyless(anyLong());
    }

    @Test
    void 같은_주문의_결제가_있으면_그대로_돌려준다() {
        Payment existing = withId(payment(1005L, null), 7L);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(existing));

        assertThat(paymentCreator.create(DRAFT)).isSameAs(existing);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 같은_고객의_이전_요청이_키_없음이면_만료하고_새로_만든다() {
        Payment previous = withId(payment(1004L, null), 6L);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty());
        given(paymentRepository.findByCustomerIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.of(previous));
        given(paymentExpirer.expireIfKeyless(6L)).willReturn(true);
        given(paymentRepository.saveAndFlush(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 7L));

        assertThat(paymentCreator.create(DRAFT).getId()).isEqualTo(7L);

        InOrder order = inOrder(paymentExpirer, paymentRepository);
        order.verify(paymentExpirer).expireIfKeyless(6L);
        order.verify(paymentRepository).saveAndFlush(any());
    }

    @Test
    void 같은_고객의_이전_요청에_키가_있으면_409_PAYMENT_IN_PROGRESS() {
        Payment previous = withId(payment(1004L, "tgen_prev"), 6L);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty());
        given(paymentRepository.findByCustomerIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.of(previous));

        assertError(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        verify(paymentExpirer, never()).expireIfKeyless(anyLong());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 만료하려는_사이에_이전_탭이_키를_저장했으면_409_PAYMENT_IN_PROGRESS() {
        Payment previous = withId(payment(1004L, null), 6L);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty());
        given(paymentRepository.findByCustomerIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.of(previous));
        given(paymentExpirer.expireIfKeyless(6L)).willReturn(false);

        assertError(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 같은_주문이_동시에_저장되면_먼저_저장된_결제를_돌려준다() {
        Payment existing = withId(payment(1005L, null), 7L);
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty(), Optional.of(existing));
        given(paymentRepository.findByCustomerIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("payment_order_id_uq"));

        assertThat(paymentCreator.create(DRAFT)).isSameAs(existing);
    }

    @Test
    void 같은_고객의_다른_결제_요청이_동시에_생기면_409_PAYMENT_IN_PROGRESS() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty(), Optional.empty());
        given(paymentRepository.findByCustomerIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("payment_customer_active_uq"));

        assertError(PaymentErrorCode.PAYMENT_IN_PROGRESS);
    }

    private void assertError(PaymentErrorCode expected) {
        assertThatThrownBy(() -> paymentCreator.create(DRAFT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private static Payment payment(Long orderId, String paymentKey) {
        Payment payment = Payment.request(orderId, 1L, 10L, 3L, "싹분식", "customer@test.com", "owner@test.com",
                "BDS-" + orderId + "-1", 18000);
        ReflectionTestUtils.setField(payment, "tossPaymentKey", paymentKey);
        return payment;
    }

    private static Payment withId(Payment payment, Long id) {
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
