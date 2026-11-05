package com.baedalsak.payment.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 토스 승인 전 paymentKey 저장과, 토스가 미승인을 확인해 준 키 비우기 (#125).
 * 토스 호출과 분리된 짧은 트랜잭션으로, 승인 응답이 유실돼도 키가 남아 결제 조회로 확인할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKeyRecorder {

  private final PaymentRepository paymentRepository;

  /**
   * @return 저장했으면(또는 이미 같은 키면) true, 다른 키가 있거나 결제 요청됨이 아니면 false
   */
  @Transactional
  public boolean reserve(Long paymentId, String paymentKey) {
    return paymentRepository.reservePaymentKey(paymentId, paymentKey, PaymentStatus.REQUESTED) == 1;
  }

  /**
   * 비운 키는 대사에서 추적할 수 있게 로그로 남긴다 (CARD_PROCESSING_ERROR 처럼 카드사 쪽 처리 여부가 애매한 경우).
   * @return 비웠으면 true, 그사이 키가 바뀌었거나 결제 요청됨이 아니면 false
   */
  @Transactional
  public boolean clear(Long paymentId, String paymentKey, String tossCode) {
    boolean cleared = paymentRepository.clearPaymentKey(paymentId, paymentKey, PaymentStatus.REQUESTED) == 1;
    if (cleared) {
      log.info("토스 확정 실패로 paymentKey 비움 paymentId={}, paymentKey={}, tossCode={}", paymentId, paymentKey, tossCode);
    }
    return cleared;
  }
}
