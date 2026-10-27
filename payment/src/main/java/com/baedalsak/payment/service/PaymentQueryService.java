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
import com.baedalsak.payment.dto.response.PageResponse;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.dto.response.PaymentSummaryResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 내역 조회 (#15, 설계서 'API 명세' 5.4.1·5.4.2). 자기 DB 만으로 응답하고 다른 서비스를 호출하지 않는다.
 * 상세는 결제 ID(U-10·U-06 → U-11)와 주문 ID(U-09 주문 상세 → U-11) 두 경로로 찾는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

  // 최신순, 생성 시각이 같으면 id 역순
  private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

  private final PaymentRepository paymentRepository;

  // 내 결제 목록 최신순 (결제 요청 상태 포함)
  public PageResponse<PaymentSummaryResponse> getMyPayments(LoginMember member, int page, int size) {
    Page<Payment> payments = paymentRepository.findByCustomerId(member.id(),
        PageRequest.of(page, size, LATEST_FIRST));
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
