package com.baedalsak.payment.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.payment.client.order.OrderClient;
import com.baedalsak.payment.client.order.OrderInternal;
import com.baedalsak.payment.client.order.OrderInternal.OrderStatus;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 결제 요청 생성 (#13). order 내부 API 검증·기존 결제 재사용·동시 요청
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    static final LoginMember CUSTOMER = new LoginMember(1L, MemberRole.CUSTOMER);

    @Mock
    OrderClient orderClient;
    @Mock
    PaymentRepository paymentRepository;
    @InjectMocks
    PaymentService paymentService;

    @Test
    void 결제대기_주문이면_스냅샷과_함께_결제를_만든다() {
        given(orderClient.getOrder(1005L)).willReturn(Optional.of(order(1L, OrderStatus.PENDING_PAYMENT)));
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty());
        given(paymentRepository.saveAndFlush(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 7L));

        PaymentReadyResponse response = paymentService.request(CUSTOMER, 1005L);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        Payment payment = saved.getValue();
        assertThat(payment.getOrderId()).isEqualTo(1005L);
        assertThat(payment.getCustomerId()).isEqualTo(1L);
        assertThat(payment.getAmount()).isEqualTo(18000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getOwnerId()).isEqualTo(10L);
        assertThat(payment.getStoreId()).isEqualTo(3L);
        assertThat(payment.getStoreName()).isEqualTo("싹분식");
        assertThat(payment.getCustomerEmail()).isEqualTo("customer@test.com");
        assertThat(payment.getOwnerEmail()).isEqualTo("owner@test.com");
        assertThat(payment.getTossOrderId()).matches("BDS-1005-\\d+");

        assertThat(response.paymentId()).isEqualTo(7L);
        assertThat(response.tossOrderId()).isEqualTo(payment.getTossOrderId());
        assertThat(response.orderName()).isEqualTo("참치김밥 외 1건");
        assertThat(response.amount()).isEqualTo(18000);
    }

    @Test
    void 이미_결제_요청이_있으면_그대로_돌려준다() {
        Payment existing = withId(payment(), 7L);
        given(orderClient.getOrder(1005L)).willReturn(Optional.of(order(1L, OrderStatus.PENDING_PAYMENT)));
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(existing));

        PaymentReadyResponse response = paymentService.request(CUSTOMER, 1005L);

        assertThat(response.paymentId()).isEqualTo(7L);
        assertThat(response.tossOrderId()).isEqualTo("BDS-1005-1");
        assertThat(response.orderName()).isEqualTo("참치김밥 외 1건");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_요청으로_먼저_저장된_결제가_있으면_그것을_돌려준다() {
        Payment existing = withId(payment(), 7L);
        given(orderClient.getOrder(1005L)).willReturn(Optional.of(order(1L, OrderStatus.PENDING_PAYMENT)));
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.empty(), Optional.of(existing));
        given(paymentRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("payment_order_id_uq"));

        PaymentReadyResponse response = paymentService.request(CUSTOMER, 1005L);

        assertThat(response.paymentId()).isEqualTo(7L);
        assertThat(response.tossOrderId()).isEqualTo("BDS-1005-1");
    }

    @Test
    void 주문이_없으면_404_ORDER_NOT_FOUND() {
        given(orderClient.getOrder(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.request(CUSTOMER, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 남의_주문이면_403_FORBIDDEN() {
        given(orderClient.getOrder(1005L)).willReturn(Optional.of(order(2L, OrderStatus.PENDING_PAYMENT)));

        assertThatThrownBy(() -> paymentService.request(CUSTOMER, 1005L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
        verify(paymentRepository, never()).findByOrderId(any());
    }

    @Test
    void 결제대기가_아니면_409_ORDER_INVALID_STATUS() {
        given(orderClient.getOrder(1005L)).willReturn(Optional.of(order(1L, OrderStatus.PAID)));

        assertThatThrownBy(() -> paymentService.request(CUSTOMER, 1005L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.ORDER_INVALID_STATUS);
        verify(paymentRepository, never()).findByOrderId(any());
    }

    private static OrderInternal order(Long customerId, OrderStatus status) {
        return new OrderInternal(18000, customerId, status, 10L, 3L, "싹분식",
                "customer@test.com", "owner@test.com", "참치김밥 외 1건");
    }

    private static Payment payment() {
        return Payment.request(1005L, 1L, 10L, 3L, "싹분식", "customer@test.com", "owner@test.com",
                "BDS-1005-1", 18000);
    }

    private static Payment withId(Payment payment, Long id) {
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
