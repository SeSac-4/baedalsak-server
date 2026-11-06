package com.baedalsak.payment.service;

import java.time.OffsetDateTime;
import java.util.List;
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
 * 명령 처리(#127)와 재처리 API(#153)가 같이 쓴다. 재처리는 명령 ID 가 없어 messageId 를 null 로 넘긴다.
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
    // 진행 중이 보통이고, 재처리(#153)에서는 확정 거절(FAILED) 뒤 토스가 실제로 취소된 경우도 완료로 맞춘다
    paymentCancelRepository.markDone(cancel.getId(), transactionKey, canceledAt,
        List.of(PaymentCancelStatus.PENDING, PaymentCancelStatus.FAILED), PaymentCancelStatus.DONE);
    outboxAppender.append(PaymentCanceledEvent.TYPE, PaymentCanceledEvent.ROUTING_KEY, payment.getId(),
        new PaymentCanceledEvent(payment.getId(), payment.getOrderId(), cancel.getAmount(), canceledAt));
    markProcessed(messageId);
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
    markProcessed(messageId);
    return true;
  }

  /**
   * 재처리(#153): 환불 처리 중인데 이전 취소가 확정 거절(FAILED)로 끝나 진행 중 행이 없을 때, 새 진행 중 취소 행(새 멱등 키)을 만든다.
   * 결제는 이미 환불 처리 중이라 상태를 바꾸지 않는다. 그 사이 다른 재처리가 먼저 만들었으면(진행 중 1건 부분 UNIQUE) 빈 값.
   */
  @Transactional
  public Optional<PaymentCancel> restart(Payment payment, PaymentCancel failed) {
    if (paymentCancelRepository.findByPaymentIdAndStatus(payment.getId(), PaymentCancelStatus.PENDING).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(paymentCancelRepository.saveAndFlush(
        PaymentCancel.fullCancel(payment, failed.getReason(), failed.getRequestedBy())));
  }

  // 명령으로 처리한 경우에만 processed_event 를 남긴다 (재처리 · 대사는 명령이 없어 null)
  private void markProcessed(UUID messageId) {
    if (messageId != null) {
      processedEventRepository.insertIfAbsent(messageId, CancelPaymentCommand.TYPE);
    }
  }
}
