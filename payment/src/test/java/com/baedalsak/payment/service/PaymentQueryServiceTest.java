package com.baedalsak.payment.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.response.PageResponse;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.dto.response.PaymentSummaryResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 결제 내역 조회 (#15). 목록 페이지 응답, 상세(결제 ID·주문 ID) 404·403
 */
@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    static final LoginMember CUSTOMER = new LoginMember(1L, MemberRole.CUSTOMER);
    static final OffsetDateTime APPROVED_AT = OffsetDateTime.parse("2026-10-26T15:30:00+09:00");

    @Mock
    PaymentRepository paymentRepository;
    @InjectMocks
    PaymentQueryService paymentQueryService;

    @Test
    void 내_결제_목록을_최신순_페이지로_돌려준다() {
        Payment approved = payment(8L, 1006L, 1L, 19500);
        ReflectionTestUtils.setField(approved, "status", PaymentStatus.APPROVED);
        ReflectionTestUtils.setField(approved, "approvedAt", APPROVED_AT);
        Payment requested = payment(7L, 1005L, 1L, 18000);
        // 최신순, 같으면 id 역순으로 정렬을 넘기는지까지 확인
        PageRequest latestFirst = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        given(paymentRepository.findByCustomerId(1L, latestFirst))
                .willReturn(new PageImpl<>(List.of(approved, requested), latestFirst, 3));

        PageResponse<PaymentSummaryResponse> response = paymentQueryService.getMyPayments(CUSTOMER, 0, 2);

        assertThat(response.content()).extracting(PaymentSummaryResponse::paymentId).containsExactly(8L, 7L);
        assertThat(response.content().get(0)).isEqualTo(
                new PaymentSummaryResponse(8L, "싹분식", PaymentStatus.APPROVED, 19500, APPROVED_AT));
        // 결제 요청 상태도 포함, 승인 시각은 없음
        assertThat(response.content().get(1).status()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(response.content().get(1).approvedAt()).isNull();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void 결제_ID로_상세를_돌려준다() {
        Payment approved = payment(7L, 1005L, 1L, 18000);
        ReflectionTestUtils.setField(approved, "status", PaymentStatus.APPROVED);
        ReflectionTestUtils.setField(approved, "approvalNumber", "30024234");
        given(paymentRepository.findById(7L)).willReturn(Optional.of(approved));

        PaymentDetailResponse response = paymentQueryService.getPayment(CUSTOMER, 7L);

        assertThat(response.paymentId()).isEqualTo(7L);
        assertThat(response.orderId()).isEqualTo(1005L);
        assertThat(response.amount()).isEqualTo(18000);
        assertThat(response.approvalNumber()).isEqualTo("30024234");
    }

    @Test
    void 주문_ID로_상세를_돌려준다() {
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(payment(7L, 1005L, 1L, 18000)));

        PaymentDetailResponse response = paymentQueryService.getPaymentByOrder(CUSTOMER, 1005L);

        assertThat(response.paymentId()).isEqualTo(7L);
        assertThat(response.orderId()).isEqualTo(1005L);
        // 승인 전이거나 카드 결제가 아니면 승인번호는 null
        assertThat(response.approvalNumber()).isNull();
    }

    @Test
    void 결제가_없으면_404_PAYMENT_NOT_FOUND() {
        given(paymentRepository.findById(9L)).willReturn(Optional.empty());
        given(paymentRepository.findByOrderId(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentQueryService.getPayment(CUSTOMER, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        assertThatThrownBy(() -> paymentQueryService.getPaymentByOrder(CUSTOMER, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void 남의_결제면_403_FORBIDDEN() {
        given(paymentRepository.findById(7L)).willReturn(Optional.of(payment(7L, 1005L, 2L, 18000)));
        given(paymentRepository.findByOrderId(1005L)).willReturn(Optional.of(payment(7L, 1005L, 2L, 18000)));

        assertThatThrownBy(() -> paymentQueryService.getPayment(CUSTOMER, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> paymentQueryService.getPaymentByOrder(CUSTOMER, 1005L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    private static Payment payment(Long id, Long orderId, Long customerId, int amount) {
        Payment payment = Payment.request(orderId, customerId, 10L, 3L, "싹분식", "customer@test.com",
                "owner@test.com", "BDS-" + orderId + "-1", amount);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
