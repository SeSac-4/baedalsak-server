package com.baedalsak.payment.service;

import org.springframework.stereotype.Service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.payment.client.order.OrderClient;
import com.baedalsak.payment.client.order.OrderInternal;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.error.PaymentErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 결제 요청 생성 — 고객 API (#13). order 내부 API 로 금액·상태를 검증하고 스냅샷을 저장한다.
 * order 호출 중에 DB 트랜잭션을 잡지 않도록 메서드 전체를 트랜잭션으로 묶지 않는다.
 * 저장 · 고객당 진행 중 결제 1건은 PaymentCreator (#128). 주문 · 결제 오케스트레이션 전환 뒤에는 내부 API(PaymentInternalService)만 쓰고
 * 이 고객 API 는 삭제한다 (#129).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final OrderClient orderClient;
  private final PaymentCreator paymentCreator;

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
    Payment payment = paymentCreator.create(new PaymentDraft(orderId, order.totalAmount(), member.id(),
        order.ownerId(), order.storeId(), order.storeName(), order.customerEmail(), order.ownerEmail()));
    // 같은 고객의 새 결제 요청에 밀려 만료된 주문은 더 이상 결제할 수 없다
    if (payment.getStatus() == PaymentStatus.EXPIRED) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_INVALID_STATUS);
    }
    // 주문명은 저장하지 않고 매번 order 응답 값을 쓴다 (주문 항목은 생성 후 바뀌지 않음)
    return new PaymentReadyResponse(payment.getId(), payment.getTossOrderId(), order.orderName(), payment.getAmount());
  }
}
