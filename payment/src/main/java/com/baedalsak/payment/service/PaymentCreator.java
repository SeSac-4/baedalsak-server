package com.baedalsak.payment.service;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 요청 생성 (#128). 주문당 결제 1건(같은 주문이면 기존 결제를 돌려줌)과 고객당 진행 중 결제 요청 1건을 지킨다.
 * <ul>
 *   <li>같은 고객의 진행 중(REQUESTED) 결제가 결제창만 열린 상태(키 없음)면 만료하고 새로 만든다</li>
 *   <li>키가 저장돼 있으면(승인 진행 중) 409 PAYMENT_IN_PROGRESS — 두 탭 이중 결제 방지</li>
 * </ul>
 * 저장은 saveAndFlush 자체 트랜잭션, 만료는 PaymentExpirer 의 짧은 트랜잭션.
 */
@Component
@RequiredArgsConstructor
public class PaymentCreator {

  private final PaymentRepository paymentRepository;
  private final PaymentExpirer paymentExpirer;

  /** 결제 요청을 만들거나, 같은 주문의 결제가 이미 있으면 그대로 돌려준다 (상태 확인은 호출하는 쪽) */
  public Payment create(PaymentDraft draft) {
    Payment existing = paymentRepository.findByOrderId(draft.orderId()).orElse(null);
    if (existing != null) {
      return existing;
    }
    paymentRepository.findByCustomerIdAndStatus(draft.customerId(), PaymentStatus.REQUESTED)
        .ifPresent(this::releaseActive);
    try {
      return paymentRepository.saveAndFlush(draft.toPayment(tossOrderId(draft.orderId())));
    } catch (DataIntegrityViolationException e) {
      // 같은 주문이 동시에 요청됨(order_id UNIQUE) → 먼저 저장된 결제
      return paymentRepository.findByOrderId(draft.orderId())
          // 같은 고객의 다른 결제 요청이 동시에 생김(payment_customer_active_uq)
          .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_IN_PROGRESS));
    }
  }

  // 이전 결제 요청: 키 없음이면 만료, 키가 있거나 그 사이 키가 저장됐으면 승인 진행 중
  private void releaseActive(Payment active) {
    if (active.getTossPaymentKey() != null || !paymentExpirer.expireIfKeyless(active.getId())) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
    }
  }

  // 토스 orderId: 6~64자, 영문·숫자·-·_
  private static String tossOrderId(Long orderId) {
    return "BDS-" + orderId + "-" + Instant.now().getEpochSecond();
  }
}
