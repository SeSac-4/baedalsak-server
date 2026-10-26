package com.baedalsak.payment.service;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.payment.client.OrderClient;
import com.baedalsak.payment.client.OrderInternal;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.error.PaymentErrorCode;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 요청 생성 (#13). order 내부 API 로 금액·상태를 검증하고 스냅샷을 저장한다.
 * order 호출 중에 DB 트랜잭션을 잡지 않도록 메서드 전체를 트랜잭션으로 묶지 않는다 (저장은 saveAndFlush 자체 트랜잭션).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final OrderClient orderClient;
  private final PaymentRepository paymentRepository;

  public PaymentReadyResponse request(LoginMember member, Long orderId) {
    OrderInternal order = orderClient.getOrder(orderId)
        .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));
    if (!order.customerId().equals(member.id())) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
    if (!order.isPendingPayment()) {
      throw new BusinessException(PaymentErrorCode.ORDER_INVALID_STATUS);
    }

    // 같은 주문으로 다시 요청하면 (새로고침·재진입) 기존 결제 요청을 그대로 돌려준다
    Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseGet(() -> create(member, orderId, order));
    // 주문명은 저장하지 않고 매번 order 응답 값을 쓴다 (주문 항목은 생성 후 바뀌지 않음)
    return new PaymentReadyResponse(payment.getId(), payment.getTossOrderId(), order.orderName(), payment.getAmount());
  }

  private Payment create(LoginMember member, Long orderId, OrderInternal order) {
    Payment payment = Payment.request(orderId, member.id(), order.ownerId(), order.storeId(), order.storeName(),
        order.customerEmail(), order.ownerEmail(), tossOrderId(orderId), order.totalAmount());
    try {
      return paymentRepository.saveAndFlush(payment);
    } catch (DataIntegrityViolationException e) {
      // 동시 요청으로 같은 주문의 결제가 먼저 저장된 경우 (order_id UNIQUE)
      return paymentRepository.findByOrderId(orderId).orElseThrow(() -> e);
    }
  }

  // 토스 orderId: 6~64자, 영문·숫자·-·_
  private String tossOrderId(Long orderId) {
    return "BDS-" + orderId + "-" + Instant.now().getEpochSecond();
  }
}
