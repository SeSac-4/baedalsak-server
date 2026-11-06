package com.baedalsak.payment.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.payment.client.toss.TossApiException;
import com.baedalsak.payment.client.toss.TossConfirmResponse;
import com.baedalsak.payment.client.toss.TossPaymentsClient;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.request.InternalCreatePaymentRequest;
import com.baedalsak.payment.dto.response.ExpireResultResponse;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.dto.response.PaymentStatusResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * payment 내부 API (#128). 주문 · 결제 오케스트레이션 사가의 조정자(order)가 호출한다.
 * 결제 요청 생성 · 상태 일괄 조회 · 만료. 승인은 PaymentConfirmService.confirmInternal.
 * 토스 호출 중에 DB 트랜잭션을 잡지 않도록 저장은 각 컴포넌트의 짧은 트랜잭션에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentInternalService {

  private final PaymentRepository paymentRepository;
  private final PaymentCreator paymentCreator;
  private final PaymentExpirer paymentExpirer;
  private final PaymentApprover paymentApprover;
  private final TossPaymentsClient tossPaymentsClient;

  /**
   * 결제 요청 생성 (5.1.1). 같은 주문으로 다시 부르면 기존 결제 요청을 돌려준다(조정자의 타임아웃 재호출).
   * 기존 결제가 결제 요청됨이 아니면(만료 · 승인) 409 ORDER_INVALID_STATUS.
   */
  public PaymentReadyResponse create(InternalCreatePaymentRequest request) {
    Payment payment = paymentCreator.create(new PaymentDraft(request.orderId(), request.amount(),
        request.customerId(), request.ownerId(), request.storeId(), request.storeName(),
        request.customerEmail(), request.ownerEmail()));
    if (payment.getStatus() != PaymentStatus.REQUESTED) {
      throw new BusinessException(PaymentErrorCode.ORDER_INVALID_STATUS);
    }
    return new PaymentReadyResponse(payment.getId(), payment.getTossOrderId(), request.orderName(), payment.getAmount());
  }

  /** 결제 상태 일괄 조회 (조정자 점검). 결제가 없는 주문은 결과에 없다 */
  public List<PaymentStatusResponse> findStatuses(Collection<Long> orderIds) {
    return paymentRepository.findByOrderIdIn(orderIds).stream()
        .map(PaymentStatusResponse::from)
        .toList();
  }

  /**
   * 결제 요청 만료 (조정자 점검, 결제대기 45분). 토스 결제창 30분 + 인증 후 승인 10분이 지나 새로 승인될 수 없는 시점이다.
   * <ul>
   *   <li>키 없음: 돈이 나갈 수 없으므로 토스 조회 없이 만료</li>
   *   <li>키 있음: 토스 결제 조회 → 승인 완료 · 금액 · 주문번호 일치면 승인으로 기록하고 APPROVED (조정자가 10분 규칙으로 결제완료 또는 환불),
   *       아니면 만료</li>
   *   <li>토스 조회 실패 · 금액 불일치: 결과를 정하지 않고 502 (조정자가 다음 주기에 다시)</li>
   * </ul>
   * 승인을 기록해야 조정자가 보내는 결제 취소 명령(승인 완료 결제만 환불)이 동작한다.
   */
  public List<ExpireResultResponse> expire(Collection<Long> orderIds) {
    List<ExpireResultResponse> results = new ArrayList<>();
    for (Long orderId : orderIds) {
      results.add(paymentRepository.findByOrderId(orderId)
          .map(this::expire)
          .orElseGet(() -> ExpireResultResponse.none(orderId)));
    }
    return results;
  }

  private ExpireResultResponse expire(Payment payment) {
    switch (payment.getStatus()) {
      // 환불 처리 중 · 환불 완료(#127)도 승인됐던 결제 → 승인 정보 응답 (조정자의 10분 규칙이 다시 명령을 보내도 payment 는 상태로 무시)
      case APPROVED, CANCEL_REQUESTED, CANCELED -> {
        return ExpireResultResponse.approved(payment.getOrderId(), payment.getApprovedAt());
      }
      case EXPIRED -> {
        return ExpireResultResponse.expired(payment.getOrderId());
      }
      default -> {
        if (payment.getTossPaymentKey() == null && paymentExpirer.expireIfKeyless(payment.getId())) {
          return ExpireResultResponse.expired(payment.getOrderId());
        }
        // 키가 있거나, 만료 직전에 키가 저장됨 → 토스로 확인
        Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
        if (current.getStatus() != PaymentStatus.REQUESTED) {
          return expire(current);
        }
        return expireAfterTossCheck(current);
      }
    }
  }

  private ExpireResultResponse expireAfterTossCheck(Payment payment) {
    TossConfirmResponse found;
    try {
      found = tossPaymentsClient.getPayment(payment.getTossPaymentKey(), payment.getTossOrderId());
    } catch (TossApiException e) {
      log.warn("만료 전 토스 결제 조회 실패, 다음 점검에서 다시 orderId={}, code={}", payment.getOrderId(), e.getCode());
      throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }
    if (!found.isDone()) {
      if (paymentExpirer.expireWithKey(payment.getId(), payment.getTossPaymentKey())) {
        return ExpireResultResponse.expired(payment.getOrderId());
      }
      return expire(paymentRepository.findById(payment.getId()).orElseThrow());   // 그 사이 상태가 바뀜
    }
    if (!payment.getAmount().equals(found.totalAmount()) || !payment.getTossOrderId().equals(found.orderId())) {
      log.error("만료 전 토스 조회가 승인 완료지만 금액 · 주문번호가 다름 orderId={}, paymentId={}",
          payment.getOrderId(), payment.getId());
      throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }
    log.warn("만료 전 토스 조회에서 승인 발견, 승인으로 기록 orderId={}, paymentId={}", payment.getOrderId(), payment.getId());
    try {
      Payment approved = paymentApprover.approve(payment.getId(), found);
      return ExpireResultResponse.approved(approved.getOrderId(), approved.getApprovedAt());
    } catch (BusinessException e) {
      // 그 사이 승인 기록됨(고객의 결제 확인 등) → 지금 상태로 다시 판단
      return expire(paymentRepository.findById(payment.getId()).orElseThrow());
    }
  }
}
