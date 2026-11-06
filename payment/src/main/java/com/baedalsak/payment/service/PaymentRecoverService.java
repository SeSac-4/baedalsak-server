package com.baedalsak.payment.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossCancelClient;
import com.baedalsak.payment.client.toss.TossCancelResponse;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.response.RecoverResultResponse;
import com.baedalsak.payment.dto.response.RecoverResultResponse.Action;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재처리 (#153, 설계서 '이벤트 목록' PaymentCancelFailed — 재처리는 payment 내부 재처리 API).
 * 결제 하나를 토스에 조회해 DB 상태와 비교하고, 안전한 건 정리하고 나머지는 상태만 돌려준다. PG 대사(#154)도 결제마다 이 처리를 부른다.
 * <ul>
 *   <li>환불 처리 중 + 토스 취소 → 환불 완료 기록</li>
 *   <li>환불 처리 중 + 진행 중 취소 + 토스 승인 → 같은 멱등 키로 토스 취소 다시</li>
 *   <li>환불 처리 중 + 확정 거절(FAILED) + 토스 승인 → 새 취소 행(새 멱등 키)으로 다시 취소 (이전 키는 거절로 끝남)</li>
 *   <li>결제 요청됨 → 바꾸지 않고 토스 상태만 (콘솔 취소 뒤에는 order 점검의 만료 호출이 정리)</li>
 *   <li>그 밖 → 409 PAYMENT_INVALID_STATUS</li>
 * </ul>
 * 토스 취소 409 IDEMPOTENT_REQUEST_PROCESSING 은 같은 취소가 다른 경로에서 처리 중이라 이번엔 건너뛴다 (알림 아님).
 * 토스 조회가 실패하면 결과를 정하지 않고 502.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoverService {

  static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";

  private final PaymentRepository paymentRepository;
  private final PaymentCancelRepository paymentCancelRepository;
  private final PaymentCancelRecorder recorder;
  private final TossCancelClient tossCancelClient;

  public RecoverResultResponse recover(Long paymentId) {
    Payment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    if (payment.getStatus() != PaymentStatus.CANCEL_REQUESTED && payment.getStatus() != PaymentStatus.REQUESTED) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_INVALID_STATUS);
    }
    if (payment.getTossPaymentKey() == null) {
      // 결제창만 열린 결제 요청 — 토스에 조회할 결제가 없음 (만료 호출이 정리)
      throw new BusinessException(PaymentErrorCode.PAYMENT_INVALID_STATUS);
    }
    TossCancelResponse toss = lookup(payment);
    Action action = payment.getStatus() == PaymentStatus.REQUESTED
        ? Action.NO_CHANGE
        : recoverCancel(payment, toss);
    Payment after = paymentRepository.findById(paymentId).orElseThrow();
    log.info("재처리 paymentId={}, orderId={}, before={}, tossStatus={}, action={}, after={}",
        paymentId, payment.getOrderId(), payment.getStatus(), toss.status(), action, after.getStatus());
    return new RecoverResultResponse(paymentId, payment.getOrderId(), payment.getStatus(), toss.status(), action,
        after.getStatus());
  }

  private Action recoverCancel(Payment payment, TossCancelResponse toss) {
    PaymentCancel latest = paymentCancelRepository.findTopByPaymentIdOrderByIdDesc(payment.getId())
        .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_INVALID_STATUS));
    if (toss.isCanceled()) {
      complete(payment, latest, toss);
      return Action.RECORDED_CANCELED;
    }
    if (!toss.isApproved()) {
      log.error("재처리: 환불 처리 중인데 토스 상태가 승인 · 취소가 아님, 확인 필요 paymentId={}, tossStatus={}",
          payment.getId(), toss.status());
      return Action.CHECK_NEEDED;
    }
    if (latest.getStatus() == PaymentCancelStatus.DONE) {
      // 결제 · 취소 행 상태는 같은 트랜잭션에서 바뀌어 생기지 않아야 하는 조합
      log.error("재처리: 환불 처리 중인데 취소 행이 완료, 확인 필요 paymentId={}, cancelId={}", payment.getId(), latest.getId());
      return Action.CHECK_NEEDED;
    }
    Optional<PaymentCancel> toRun = latest.getStatus() == PaymentCancelStatus.FAILED
        ? restart(payment, latest)
        : Optional.of(latest);
    return toRun.map(cancel -> cancelAtToss(payment, cancel)).orElse(Action.IN_PROGRESS);
  }

  // 확정 거절 뒤 새 취소 행. 동시에 다른 재처리가 먼저 만들었으면 그쪽이 처리 중
  private Optional<PaymentCancel> restart(Payment payment, PaymentCancel failed) {
    try {
      return recorder.restart(payment, failed);
    } catch (DataIntegrityViolationException e) {
      return Optional.empty();
    }
  }

  private Action cancelAtToss(Payment payment, PaymentCancel cancel) {
    recorder.countAttempt(cancel.getId());
    TossCancelResponse response;
    try {
      response = tossCancelClient.cancel(payment.getTossPaymentKey(), cancel.getReason(), cancel.getIdempotencyKey(),
          payment.getId());
    } catch (TossApiException e) {
      if (IDEMPOTENT_REQUEST_PROCESSING.equals(e.getCode())) {
        return Action.IN_PROGRESS;
      }
      return switch (TossCancelFailure.of(e)) {
        case RETRY -> Action.UNKNOWN;   // 진행 중 그대로 (같은 키 보존), 나중에 다시
        case CHECK -> recheck(payment, cancel);
        case REJECTED -> {
          log.error("재처리: 토스 취소 확정 거절 paymentId={}, orderId={}, code={}",
              payment.getId(), payment.getOrderId(), e.getCode());
          recorder.fail(payment, cancel, e.getCode(), e.getTossMessage(), null);
          yield Action.CANCEL_REJECTED;
        }
      };
    }
    if (!response.isCanceled()) {
      log.error("재처리: 토스 취소 응답이 CANCELED 가 아님 paymentId={}, tossStatus={}", payment.getId(), response.status());
      return Action.CHECK_NEEDED;
    }
    complete(payment, cancel, response);
    return Action.CANCELED;
  }

  // 토스가 '이미 취소됨' 류로 답함 → 다시 조회해 취소면 완료, 아니면 진행 중 그대로
  private Action recheck(Payment payment, PaymentCancel cancel) {
    TossCancelResponse found;
    try {
      found = tossCancelClient.getPayment(payment.getTossPaymentKey(), payment.getId());
    } catch (TossApiException e) {
      return Action.UNKNOWN;
    }
    if (!found.isCanceled()) {
      return Action.UNKNOWN;
    }
    complete(payment, cancel, found);
    return Action.RECORDED_CANCELED;
  }

  private void complete(Payment payment, PaymentCancel cancel, TossCancelResponse response) {
    TossCancelResponse.Cancel last = response.lastCancel();
    String transactionKey = last == null ? null : last.transactionKey();
    OffsetDateTime canceledAt = last == null || last.canceledAt() == null ? OffsetDateTime.now() : last.canceledAt();
    recorder.complete(payment, cancel, transactionKey, canceledAt, null);
  }

  private TossCancelResponse lookup(Payment payment) {
    try {
      return tossCancelClient.getPayment(payment.getTossPaymentKey(), payment.getId());
    } catch (TossApiException e) {
      log.warn("재처리: 토스 결제 조회 실패, 결과를 정하지 않음 paymentId={}, code={}", payment.getId(), e.getCode());
      throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }
  }
}
