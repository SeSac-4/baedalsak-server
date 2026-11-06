package com.baedalsak.payment.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 요청 만료 (#128). 각각 짧은 트랜잭션으로, 토스 호출 중에 DB 트랜잭션을 잡지 않게 호출하는 쪽과 분리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirer {

  private final PaymentRepository paymentRepository;

  /**
   * 결제창만 열린(키 없음) 결제 요청을 만료한다. 그 사이 키가 저장됐으면(승인 진행 중) false.
   * 키 저장도 status='REQUESTED' 조건부라 둘이 동시에 와도 하나만 성공한다.
   */
  @Transactional
  public boolean expireIfKeyless(Long paymentId) {
    boolean expired = paymentRepository.expireIfKeyless(paymentId, PaymentStatus.REQUESTED, PaymentStatus.EXPIRED) == 1;
    if (expired) {
      log.info("결제 요청 만료(키 없음) paymentId={}", paymentId);
    }
    return expired;
  }

  /** 토스 조회로 승인되지 않았음을 확인한 결제 요청을 만료한다. 저장된 키가 그대로일 때만 */
  @Transactional
  public boolean expireWithKey(Long paymentId, String paymentKey) {
    boolean expired = paymentRepository.expireWithKey(paymentId, paymentKey, PaymentStatus.REQUESTED,
        PaymentStatus.EXPIRED) == 1;
    if (expired) {
      log.info("결제 요청 만료(토스 미승인 확인) paymentId={}", paymentId);
    }
    return expired;
  }
}
