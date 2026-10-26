package com.baedalsak.payment.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.client.toss.TossConfirmResponse;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.event.OutboxAppender;
import com.baedalsak.payment.event.PaymentApprovedEvent;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 토스 승인 후 결제 APPROVED 갱신과 PaymentApproved outbox 저장을 한 트랜잭션으로 묶는다 (설계서 'API 명세' 5.2.1, docs 3-2).
 * 조건부 갱신(status='REQUESTED')이 0행이면 이미 승인된 결제 → 409, outbox 는 저장하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentApprover {

  private final PaymentRepository paymentRepository;
  private final OutboxAppender outboxAppender;

  @Transactional
  public Payment approve(Long paymentId, TossConfirmResponse approved) {
    int updated = paymentRepository.approve(paymentId, approved.paymentKey(), approved.method(),
        approved.approvedAt(), approved.receiptUrl(), PaymentStatus.REQUESTED, PaymentStatus.APPROVED);
    if (updated == 0) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
    }
    Payment payment = paymentRepository.findById(paymentId).orElseThrow();
    outboxAppender.append(PaymentApprovedEvent.TYPE, PaymentApprovedEvent.ROUTING_KEY, payment.getId(),
        PaymentApprovedEvent.from(payment));
    return payment;
  }
}
