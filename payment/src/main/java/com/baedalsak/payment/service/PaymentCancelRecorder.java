package com.baedalsak.payment.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.payment.domain.CancelRequester;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.event.CancelPaymentCommand;
import com.baedalsak.payment.event.OutboxAppender;
import com.baedalsak.payment.event.PaymentCancelFailedEvent;
import com.baedalsak.payment.event.PaymentCanceledEvent;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import com.baedalsak.payment.repository.PaymentRepository;
import com.baedalsak.payment.repository.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 취소의 DB 기록 (#127). 토스 호출 중에 트랜잭션을 잡지 않도록 단계마다 짧은 트랜잭션으로 나눈다.
 * payment.status 와 payment_cancel.status 는 같은 트랜잭션에서 함께 바꾸고, 바꿀 때는 모두 조건부 UPDATE (0행이면 다른 처리가 먼저 함).
 */
@Component
@RequiredArgsConstructor
public class PaymentCancelRecorder {

  private final PaymentRepository paymentRepository;
  private final PaymentCancelRepository paymentCancelRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final OutboxAppender outboxAppender;

  /**
   * 트랜잭션 1: 승인 완료 → 환불 처리 중 + 진행 중 취소 행 (멱등 키 새로 발급). 토스 호출 전에 저장해 결과를 모르는 환불도 남긴다.
   * @return 0행(그 사이 상태가 바뀜)이면 빈 값 → 호출한 쪽이 다시 읽어 판단
   */
  @Transactional
  public Optional<PaymentCancel> start(Payment payment, String reason, CancelRequester requestedBy) {
    if (paymentRepository.changeStatus(payment.getId(), PaymentStatus.APPROVED, PaymentStatus.CANCEL_REQUESTED) == 0) {
      return Optional.empty();
    }
    return Optional.of(paymentCancelRepository.save(PaymentCancel.fullCancel(payment, reason, requestedBy)));
  }

  // 토스 취소를 부르기 직전에 호출 횟수 +1 (재시도 · 재전달까지 누적)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void countAttempt(Long cancelId) {
    paymentCancelRepository.increaseAttempt(cancelId);
  }

  /**
   * 트랜잭션 2: 환불 처리 중 → 환불 완료 + 취소 완료 + PaymentCanceled outbox + processed_event.
   * @return 0행(이미 다른 처리가 완료)이면 false — outbox 를 다시 쓰지 않는다
   */
  @Transactional
  public boolean complete(Payment payment, PaymentCancel cancel, String transactionKey, OffsetDateTime canceledAt,
      UUID messageId) {
    if (paymentRepository.changeStatus(payment.getId(), PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCELED) == 0) {
      return false;
    }
    paymentCancelRepository.markDone(cancel.getId(), transactionKey, canceledAt,
        PaymentCancelStatus.PENDING, PaymentCancelStatus.DONE);
    outboxAppender.append(PaymentCanceledEvent.TYPE, PaymentCanceledEvent.ROUTING_KEY, payment.getId(),
        new PaymentCanceledEvent(payment.getId(), payment.getOrderId(), cancel.getAmount(), canceledAt));
    processedEventRepository.insertIfAbsent(messageId, CancelPaymentCommand.TYPE);
    return true;
  }

  /**
   * 토스 확정 거절: 취소 실패 + PaymentCancelFailed outbox + processed_event. 결제는 환불 처리 중으로 남긴다 (다시 취소는 재처리 API).
   * @return 0행(이미 다른 처리가 끝냄)이면 false
   */
  @Transactional
  public boolean fail(Payment payment, PaymentCancel cancel, String tossCode, String tossMessage, UUID messageId) {
    if (paymentCancelRepository.markFailed(cancel.getId(), PaymentCancelStatus.PENDING, PaymentCancelStatus.FAILED) == 0) {
      return false;
    }
    outboxAppender.append(PaymentCancelFailedEvent.TYPE, PaymentCancelFailedEvent.ROUTING_KEY, payment.getId(),
        new PaymentCancelFailedEvent(payment.getId(), payment.getOrderId(), tossCode, tossMessage));
    processedEventRepository.insertIfAbsent(messageId, CancelPaymentCommand.TYPE);
    return true;
  }
}
