package com.baedalsak.payment.dto.response;

import java.time.OffsetDateTime;

import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;

public record PaymentDetailResponse(Long paymentId,
  Long orderId,              // 주문 상세 화면 이동용
  PaymentStatus status,
  Integer amount,
  String method,             // 결제수단 (토스 응답값)
  OffsetDateTime approvedAt,
  String receiptUrl,         // 영수증 링크
  String approvalNumber) {   // 카드사 승인 번호. 카드 결제가 아니거나 승인 전이면 null

  public static PaymentDetailResponse from(Payment payment) {
    return new PaymentDetailResponse(
      payment.getId(),
      payment.getOrderId(),
      payment.getStatus(),
      payment.getAmount(),
      payment.getMethod(),
      payment.getApprovedAt(),
      payment.getReceiptUrl(),
      payment.getApprovalNumber());
  }
}
