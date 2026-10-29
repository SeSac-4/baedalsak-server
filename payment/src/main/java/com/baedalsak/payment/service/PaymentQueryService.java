package com.baedalsak.payment.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.response.PageResponse;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.dto.response.PaymentSummaryResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 내역 조회 (#15·#93, 설계서 'API 명세' 5.4.1·5.4.2). 자기 DB 만으로 응답하고 다른 서비스를 호출하지 않는다.
 * 목록은 승인 완료만, 상세(결제 ID·주문 ID)는 결제 요청 상태도 조회한다.
 * 상세는 결제 ID(U-10·U-06 → U-11)와 주문 ID(U-09 주문 상세 → U-11) 두 경로로 찾는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

  // 승인 시각 최신순, 같으면 id 역순 (#93)
  private static final Sort LATEST_APPROVED_FIRST = Sort.by(Sort.Direction.DESC, "approvedAt", "id");

  private final PaymentRepository paymentRepository;

  // 내 결제 목록 (#93): 실제로 결제된 승인 완료만, 최근 결제가 위로. 결제창만 열고 닫은 결제 요청은 제외
  public PageResponse<PaymentSummaryResponse> getMyPayments(LoginMember member, int page, int size) {
    Page<Payment> payments = paymentRepository.findByCustomerIdAndStatus(member.id(), PaymentStatus.APPROVED,
        PageRequest.of(page, size, LATEST_APPROVED_FIRST));
    return PageResponse.of(payments, payments.map(PaymentSummaryResponse::from).getContent());
  }

  public PaymentDetailResponse getPayment(LoginMember member, Long paymentId) {
    return toDetail(member, paymentRepository.findById(paymentId));
  }

  // 주문 상세 화면은 orderId 만 알고 있으므로 주문 ID 로 결제를 찾는다 (주문당 결제 1건)
  public PaymentDetailResponse getPaymentByOrder(LoginMember member, Long orderId) {
    return toDetail(member, paymentRepository.findByOrderId(orderId));
  }

  private PaymentDetailResponse toDetail(LoginMember member, Optional<Payment> found) {
    Payment payment = found.orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    if (!payment.getCustomerId().equals(member.id())) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
    return PaymentDetailResponse.from(payment);
  }
}
