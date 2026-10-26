package com.baedalsak.payment.service;

import org.springframework.stereotype.Service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.payment.client.toss.TossConfirmResponse;
import com.baedalsak.payment.client.toss.TossPaymentsClient;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.request.ConfirmPaymentRequest;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 승인 (#14, 설계서 'API 명세' 5.2.1). 검증 → 토스 승인(5초, 재시도 없음) → APPROVED + PaymentApproved outbox.
 * 토스 호출 중에 DB 트랜잭션을 잡지 않도록 저장만 PaymentApprover 의 트랜잭션에서 한다.
 * 승인 시점에 order 를 호출하지 않는다. 주문 결제완료는 order 가 PaymentApproved 를 받아 반영한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

  private final PaymentRepository paymentRepository;
  private final TossPaymentsClient tossPaymentsClient;
  private final PaymentApprover paymentApprover;

  public PaymentDetailResponse confirm(LoginMember member, ConfirmPaymentRequest request) {
    Payment payment = paymentRepository.findByTossOrderId(request.orderId())
        .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    if (!payment.getCustomerId().equals(member.id())) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
    // 이미 승인됐으면 토스를 다시 부르지 않는다 (confirm 재호출은 409)
    if (payment.getStatus() == PaymentStatus.APPROVED) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
    }
    if (!payment.getAmount().equals(request.amount())) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    TossConfirmResponse approved = tossPaymentsClient.confirm(request);
    return PaymentDetailResponse.from(paymentApprover.approve(payment.getId(), approved));
  }
}
