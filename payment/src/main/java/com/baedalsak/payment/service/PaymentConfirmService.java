package com.baedalsak.payment.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossConfirmResponse;
import com.baedalsak.payment.client.toss.TossPaymentsClient;
import com.baedalsak.payment.client.toss.TossProperties;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.request.ConfirmPaymentRequest;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.error.PaymentConfirmRejectedException;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 승인 (#14, 설계서 'API 명세' 5.2.1). 검증 → paymentKey 저장 → 토스 승인 → APPROVED + PaymentApproved outbox.
 * 토스 호출 중에 DB 트랜잭션을 잡지 않도록 저장은 PaymentKeyRecorder · PaymentApprover 의 짧은 트랜잭션에서 한다.
 * 승인 시점에 order 를 호출하지 않는다. 주문 결제완료는 order 가 PaymentApproved 를 받아 반영한다.
 *
 * <p>#125: 토스 호출 전에 paymentKey 를 저장해, 승인 응답이 유실돼도 결제 조회로 확인할 수 있게 한다.
 * <ul>
 *   <li>결과를 모르면(TossConfirmFailure.UNKNOWN) 같은 멱등 키로 1회 재전송, 그래도 모르면 502 (키 유지)</li>
 *   <li>ALREADY_PROCESSED_PAYMENT 면 결제 조회 1회로 확인</li>
 *   <li>확정 실패면 키를 비우고 400 PAYMENT_CONFIRM_REJECTED + 토스 메시지</li>
 *   <li>다른 키가 이미 저장돼 있으면 덮어쓰지 않고, 이전 키를 같은 멱등 키로 1번 다시 보내 결과를 확인</li>
 *   <li>전체 예산 15초: 토스 호출을 시작하기 전에 남은 시간이 호출 1건 최대(7초)보다 적으면 부르지 않고 결과 모름(ConfirmBudget)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

  private final PaymentRepository paymentRepository;
  private final TossPaymentsClient tossPaymentsClient;
  private final PaymentApprover paymentApprover;
  private final PaymentKeyRecorder paymentKeyRecorder;
  private final TossProperties tossProperties;
  private final Clock clock;

  public PaymentDetailResponse confirm(LoginMember member, ConfirmPaymentRequest request) {
    ConfirmBudget budget = ConfirmBudget.start(clock, tossProperties.confirmBudget(), tossProperties.maxCallTime());
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

    if (reserve(payment, request.paymentKey())) {
      return PaymentDetailResponse.from(confirmReserved(payment, request, budget));
    }
    // 저장 못 함: 그사이 승인됐거나, 다른 paymentKey 가 저장돼 있음
    Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
    if (current.getStatus() == PaymentStatus.APPROVED) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
    }
    if (current.getStatus() != PaymentStatus.REQUESTED || current.getTossPaymentKey() == null) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
    }
    return PaymentDetailResponse.from(resolvePreviousKey(current, request, budget));
  }

  // 토스 호출 전 paymentKey 저장. 다른 결제에 이미 저장된 키면(UNIQUE) 잘못된 요청으로 거절
  private boolean reserve(Payment payment, String paymentKey) {
    try {
      return paymentKeyRecorder.reserve(payment.getId(), paymentKey);
    } catch (DataIntegrityViolationException e) {
      log.warn("다른 결제에 이미 저장된 paymentKey 로 승인 요청 paymentId={}", payment.getId());
      throw new BusinessException(CommonErrorCode.VALIDATION_FAILED);
    }
  }

  // 키를 저장한 결제의 토스 승인. 결과를 모르면 같은 멱등 키로 1회 재전송 (설계서 N1.4 수정 필요)
  private Payment confirmReserved(Payment payment, ConfirmPaymentRequest request, ConfirmBudget budget) {
    TossConfirmResponse approved;
    try {
      approved = confirmWithOneResend(request, budget);
    } catch (TossApiException e) {
      return handleConfirmFailure(payment, request, e, budget);
    }
    return record(payment, approved);
  }

  private TossConfirmResponse confirmWithOneResend(ConfirmPaymentRequest request, ConfirmBudget budget) {
    requireBudget(budget, PaymentErrorCode.PAYMENT_GATEWAY_ERROR, request.orderId());
    try {
      return tossPaymentsClient.confirm(request);
    } catch (TossApiException e) {
      if (TossConfirmFailure.of(e) != TossConfirmFailure.UNKNOWN) {
        throw e;
      }
      // 재전송할 시간이 남지 않았으면 결과 모름(502, 키 유지) — U-06 다시 시도 · 안전망이 이어서 확인
      requireBudget(budget, PaymentErrorCode.PAYMENT_GATEWAY_ERROR, request.orderId());
      log.warn("토스 승인 결과를 몰라 같은 멱등 키로 1회 재전송 tossOrderId={}, code={}", request.orderId(), e.getCode());
      return tossPaymentsClient.confirm(request);
    }
  }

  private Payment handleConfirmFailure(Payment payment, ConfirmPaymentRequest request, TossApiException e,
      ConfirmBudget budget) {
    switch (TossConfirmFailure.of(e)) {
      case ALREADY_PROCESSED -> {
        TossConfirmResponse found = findPayment(request.paymentKey(), payment.getTossOrderId(), budget);
        if (found != null && matches(payment, found)) {
          return paymentApprover.approve(payment.getId(), found);
        }
        // 이미 처리됐다는데 조회 결과가 승인 완료 · 일치가 아님: 키를 남기고 결과 모름으로 응답 (대사 · 재처리 대상)
        log.error("ALREADY_PROCESSED_PAYMENT 지만 조회 결과가 승인 완료 · 일치가 아님 paymentId={}, tossStatus={}",
            payment.getId(), found == null ? null : found.status());
        throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
      }
      case REJECTED -> {
        paymentKeyRecorder.clear(payment.getId(), request.paymentKey(), e.getCode());
        throw new PaymentConfirmRejectedException(e.getTossMessage());
      }
      default -> throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }
  }

  // 승인 응답이 DONE 이 아니면(가상계좌 입금 대기 등) APPROVED 로 저장하지 않는다. 결제는 REQUESTED · 키 유지
  private Payment record(Payment payment, TossConfirmResponse approved) {
    if (!approved.isDone()) {
      log.error("토스 승인 응답이 DONE 이 아님 paymentId={}, tossStatus={}, method={}",
          payment.getId(), approved.status(), approved.method());
      throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }
    return paymentApprover.approve(payment.getId(), approved);
  }

  /**
   * 다른 paymentKey(A)가 저장된 결제에 새 키(B)로 승인 요청이 옴. A 를 같은 멱등 키로 1번만 다시 보내 결과를 확인한다.
   * 성공이면 A 로 승인 기록 후 409 PAYMENT_ALREADY_APPROVED (B 는 승인 안 함), 확정 실패면 A 를 비우고 B 로 진행,
   * 여전히 모르면 409 PAYMENT_KEY_MISMATCH.
   */
  private Payment resolvePreviousKey(Payment payment, ConfirmPaymentRequest request, ConfirmBudget budget) {
    String previousKey = payment.getTossPaymentKey();
    ConfirmPaymentRequest previous =
        new ConfirmPaymentRequest(previousKey, payment.getTossOrderId(), payment.getAmount());
    // 이전 키 결과를 확인할 시간이 없으면 결과 모름(409 KEY_MISMATCH) — 다시 시도에서 이어서 확인
    requireBudget(budget, PaymentErrorCode.PAYMENT_KEY_MISMATCH, payment.getTossOrderId());
    TossConfirmResponse approved;
    try {
      approved = tossPaymentsClient.confirm(previous);
    } catch (TossApiException e) {
      return handlePreviousKeyFailure(payment, request, previousKey, e, budget);
    }
    if (!approved.isDone()) {
      log.error("이전 paymentKey 재전송 응답이 DONE 이 아님 paymentId={}, tossStatus={}", payment.getId(), approved.status());
      throw new BusinessException(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
    }
    return approveWithPreviousKey(payment, approved);
  }

  private Payment handlePreviousKeyFailure(Payment payment, ConfirmPaymentRequest request, String previousKey,
      TossApiException e, ConfirmBudget budget) {
    switch (TossConfirmFailure.of(e)) {
      case ALREADY_PROCESSED -> {
        TossConfirmResponse found = findPayment(previousKey, payment.getTossOrderId(), budget);
        if (found != null && matches(payment, found)) {
          return approveWithPreviousKey(payment, found);
        }
        throw new BusinessException(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
      }
      case REJECTED -> {
        // 이전 키는 토스가 미승인을 확인해 줌 → 비우고 새 키로 진행
        if (!paymentKeyRecorder.clear(payment.getId(), previousKey, e.getCode())
            || !reserve(payment, request.paymentKey())) {
          throw new BusinessException(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
        }
        return confirmReserved(payment, request, budget);
      }
      default -> throw new BusinessException(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
    }
  }

  // 이전 키 결제가 승인돼 있었음: 이전 키로 승인을 기록하고, 새 키 요청에는 409 (U-06 이 U-09 로 이동)
  private Payment approveWithPreviousKey(Payment payment, TossConfirmResponse approved) {
    paymentApprover.approve(payment.getId(), approved);
    throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_APPROVED);
  }

  // 결제 조회 1회. 실패하거나 예산이 남지 않으면 null (결과 모름)
  private TossConfirmResponse findPayment(String paymentKey, String tossOrderId, ConfirmBudget budget) {
    if (!budget.canCall()) {
      log.warn("결제 확인 예산이 남지 않아 토스 결제 조회 안 함 tossOrderId={}, remaining={}", tossOrderId, budget.remaining());
      return null;
    }
    try {
      return tossPaymentsClient.getPayment(paymentKey, tossOrderId);
    } catch (TossApiException e) {
      return null;
    }
  }

  // 토스 호출 전 예산 확인. 남은 시간이 호출 1건 최대보다 적으면 호출하지 않고 결과 모름으로 응답 (키는 남김)
  private static void requireBudget(ConfirmBudget budget, PaymentErrorCode unknown, String tossOrderId) {
    if (!budget.canCall()) {
      log.warn("결제 확인 예산이 남지 않아 토스 호출 안 함 tossOrderId={}, remaining={}", tossOrderId, budget.remaining());
      throw new BusinessException(unknown);
    }
  }

  // 토스 결제가 승인 완료이고 금액 · 주문번호가 이 결제와 같은지
  private static boolean matches(Payment payment, TossConfirmResponse found) {
    return found.isDone()
        && payment.getAmount().equals(found.totalAmount())
        && payment.getTossOrderId().equals(found.orderId());
  }
}
