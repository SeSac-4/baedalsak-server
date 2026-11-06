package com.baedalsak.payment.dto.response;

import java.time.OffsetDateTime;

import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;

/**
 * [내부] 결제 상태 일괄 조회 응답 1건 (#128). 조정자 점검이 결제대기 주문의 결제 결과를 확인한다.
 * approvedAt: 승인 시각 (10분 규칙 기준), 승인 전이면 null
 */
public record PaymentStatusResponse(
    Long orderId,
    Long paymentId,
    PaymentStatus status,
    Integer amount,
    OffsetDateTime approvedAt
) {

  public static PaymentStatusResponse from(Payment payment) {
    return new PaymentStatusResponse(payment.getOrderId(), payment.getId(), payment.getStatus(),
        payment.getAmount(), payment.getApprovedAt());
  }
}
