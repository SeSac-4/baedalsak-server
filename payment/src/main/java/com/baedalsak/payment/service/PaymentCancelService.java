package com.baedalsak.payment.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossCancelClient;
import com.baedalsak.payment.client.toss.TossCancelResponse;
import com.baedalsak.payment.domain.CancelRequester;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.event.CancelPaymentCommand;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import com.baedalsak.payment.repository.PaymentRepository;
import com.baedalsak.payment.repository.ProcessedEventRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 결제 취소 명령 CancelPayment 처리 (#127, 설계서 '이벤트 목록' · '상태 전이'). 조정자(order)가 사장님 거절 · 고객 취소 ·
 * 늦게 승인된 결제 환불에 보낸다. 토스 전액 취소 후 PaymentCanceled / PaymentCancelFailed 로 응답한다.
 * <p>
 * 같은 결제에 명령이 또 와도(재전달 · 재시도 · 보상과 안전망처럼 메시지 ID 가 다른 명령) <b>메시지 ID 가 아니라 결제 상태로 판단</b>한다.
 * 그래서 진행 중 취소 행과 멱등 키는 결제당 하나뿐이고 토스 환불은 한 번만 일어난다.
 * <ul>
 *   <li>승인 완료 → 환불 처리 중 + 진행 중 취소 행 저장 → 토스 취소</li>
 *   <li>환불 처리 중 + 진행 중 취소 행 → 같은 멱등 키로 토스 취소부터 이어서</li>
 *   <li>환불 처리 중인데 진행 중 행 없음(이전 취소가 확정 거절) → 로그 + ack (다시 취소는 재처리 API)</li>
 *   <li>환불 완료 → ack</li>
 *   <li>결제 요청됨 · 만료(승인 전) → 환불 없이 ERROR + 바로 DLQ (정상 흐름에서 생기지 않음)</li>
 * </ul>
 * 영구 오류는 AmqpRejectAndDontRequeueException(앱 안 재시도 없이 DLQ), 결과 모름은 TossCancelRetryException(재시도 후 DLQ).
 */
@Slf4j
@Service
public class PaymentCancelService {

  static final int MAX_REASON_LENGTH = 200;

  private final PaymentRepository paymentRepository;
  private final PaymentCancelRepository paymentCancelRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final PaymentCancelRecorder recorder;
  private final TossCancelClient tossCancelClient;
  private final String defaultReason;

  public PaymentCancelService(PaymentRepository paymentRepository, PaymentCancelRepository paymentCancelRepository,
      ProcessedEventRepository processedEventRepository, PaymentCancelRecorder recorder,
      TossCancelClient tossCancelClient,
      @Value("${payment-cancel.default-reason:주문 취소에 따른 결제 취소}") String defaultReason) {
    this.paymentRepository = paymentRepository;
    this.paymentCancelRepository = paymentCancelRepository;
    this.processedEventRepository = processedEventRepository;
    this.recorder = recorder;
    this.tossCancelClient = tossCancelClient;
    this.defaultReason = defaultReason;
  }

  public void handle(UUID messageId, CancelPaymentCommand command) {
    if (processedEventRepository.existsById(messageId)) {
      log.info("이미 처리한 결제 취소 명령 messageId={}, orderId={}", messageId, command.orderId());
      return;
    }
    if (command.orderId() == null) {
      throw reject("orderId 없는 결제 취소 명령 messageId=" + messageId);
    }
    CancelRequester requestedBy = parseRequester(messageId, command);
    Optional<Payment> found = paymentRepository.findByOrderId(command.orderId());
    if (found.isEmpty()) {
      log.warn("결제가 없는 주문의 취소 명령, 처리 없이 ack messageId={}, orderId={}", messageId, command.orderId());
      return;
    }
    Payment payment = found.get();
    if (command.paymentId() != null && !command.paymentId().equals(payment.getId())) {
      log.error("결제 취소 명령의 paymentId 가 주문의 결제와 다름 messageId={}, orderId={}, paymentId={}, 실제={}",
          messageId, command.orderId(), command.paymentId(), payment.getId());
      throw reject("paymentId 불일치 messageId=" + messageId);
    }
    findCancelToRun(messageId, payment, normalizeReason(command.reason()), requestedBy)
        .ifPresent(cancel -> cancelAtToss(messageId, payment, cancel));
  }

  // 결제 상태로 이번에 토스를 부를 취소 행을 정한다. 부를 필요가 없으면 빈 값
  private Optional<PaymentCancel> findCancelToRun(UUID messageId, Payment payment, String reason,
      CancelRequester requestedBy) {
    switch (payment.getStatus()) {
      case APPROVED -> {
        Optional<PaymentCancel> started = recorder.start(payment, reason, requestedBy);
        if (started.isPresent()) {
          return started;
        }
        // 그 사이 다른 명령이 먼저 환불 처리 중으로 바꿈 (두 파드 동시 수신) → 지금 상태로 다시 판단
        Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
        return findCancelToRun(messageId, current, reason, requestedBy);
      }
      case CANCEL_REQUESTED -> {
        Optional<PaymentCancel> pending = paymentCancelRepository.findByPaymentIdAndStatus(payment.getId(),
            PaymentCancelStatus.PENDING);
        if (pending.isEmpty()) {
          log.warn("환불 처리 중이지만 진행 중 취소 없음(이전 취소 확정 거절), 재처리 API 대상 messageId={}, paymentId={}",
              messageId, payment.getId());
        }
        return pending;
      }
      case CANCELED -> {
        log.info("이미 환불 완료된 결제의 취소 명령, ack messageId={}, paymentId={}", messageId, payment.getId());
        return Optional.empty();
      }
      default -> {
        log.error("승인 전 결제의 취소 명령, 환불하지 않음 messageId={}, paymentId={}, status={}",
            messageId, payment.getId(), payment.getStatus());
        throw reject("승인 전 결제의 취소 명령 paymentId=" + payment.getId());
      }
    }
  }

  private void cancelAtToss(UUID messageId, Payment payment, PaymentCancel cancel) {
    recorder.countAttempt(cancel.getId());
    TossCancelResponse response;
    try {
      response = tossCancelClient.cancel(payment.getTossPaymentKey(), cancel.getReason(), cancel.getIdempotencyKey(),
          payment.getId());
    } catch (TossApiException e) {
      handleFailure(messageId, payment, cancel, e);
      return;
    }
    if (!response.isCanceled()) {
      log.error("토스 취소 응답이 CANCELED 가 아님, 결과 확인 필요 paymentId={}, tossStatus={}", payment.getId(), response.status());
      throw reject("토스 취소 응답 상태 확인 필요 paymentId=" + payment.getId());
    }
    complete(messageId, payment, cancel, response);
  }

  private void handleFailure(UUID messageId, Payment payment, PaymentCancel cancel, TossApiException e) {
    switch (TossCancelFailure.of(e)) {
      case RETRY -> throw new TossCancelRetryException(
          "토스 취소 결과 모름 · 잠깐의 오류, 같은 멱등 키로 다시 paymentId=" + payment.getId() + ", code=" + e.getCode(), e);
      case CHECK -> checkCanceled(messageId, payment, cancel, e);
      case REJECTED -> {
        log.error("토스 취소 확정 거절 paymentId={}, orderId={}, code={}", payment.getId(), payment.getOrderId(), e.getCode());
        recorder.fail(payment, cancel, e.getCode(), e.getTossMessage(), messageId);
      }
    }
  }

  // 이미 취소됐을 수 있는 에러 → 토스 조회가 취소면 완료로 기록, 아니면 결과 확인이 필요하므로 DLQ (진행 중 유지)
  private void checkCanceled(UUID messageId, Payment payment, PaymentCancel cancel, TossApiException cause) {
    TossCancelResponse found;
    try {
      found = tossCancelClient.getPayment(payment.getTossPaymentKey(), payment.getId());
    } catch (TossApiException e) {
      throw new TossCancelRetryException("취소 확인 조회 실패 paymentId=" + payment.getId(), e);
    }
    if (found.isCanceled()) {
      log.info("토스 조회로 취소 확인 paymentId={}, code={}", payment.getId(), cause.getCode());
      complete(messageId, payment, cancel, found);
      return;
    }
    log.error("토스 취소 에러인데 조회 결과가 취소가 아님, 확인 필요 paymentId={}, code={}, tossStatus={}",
        payment.getId(), cause.getCode(), found.status());
    throw reject("취소 확인 필요 paymentId=" + payment.getId());
  }

  private void complete(UUID messageId, Payment payment, PaymentCancel cancel, TossCancelResponse response) {
    TossCancelResponse.Cancel last = response.lastCancel();
    String transactionKey = last == null ? null : last.transactionKey();
    OffsetDateTime canceledAt = last == null || last.canceledAt() == null ? OffsetDateTime.now() : last.canceledAt();
    if (recorder.complete(payment, cancel, transactionKey, canceledAt, messageId)) {
      log.info("결제 취소 완료 paymentId={}, orderId={}, requestedBy={}, cancelAmount={}",
          payment.getId(), payment.getOrderId(), cancel.getRequestedBy(), cancel.getAmount());
    } else {
      log.info("다른 처리가 먼저 환불 완료를 기록함 paymentId={}", payment.getId());
    }
  }

  private CancelRequester parseRequester(UUID messageId, CancelPaymentCommand command) {
    try {
      return CancelRequester.valueOf(command.requestedBy());
    } catch (IllegalArgumentException | NullPointerException e) {
      log.error("결제 취소 명령의 requestedBy 가 올바르지 않음 messageId={}, requestedBy={}", messageId, command.requestedBy());
      throw reject("requestedBy 오류 messageId=" + messageId);
    }
  }

  // 사유가 없으면 기본 문구, 200자를 넘으면 글자 단위로 자른다 (토스 cancelReason 최대 200자)
  String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return defaultReason;
    }
    String trimmed = reason.strip();
    return trimmed.codePointCount(0, trimmed.length()) <= MAX_REASON_LENGTH
        ? trimmed
        : trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_REASON_LENGTH));
  }

  private static AmqpRejectAndDontRequeueException reject(String message) {
    return new AmqpRejectAndDontRequeueException(message);
  }
}
