package com.baedalsak.payment.dto.response;

import java.time.OffsetDateTime;

import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentStatus;

public record PaymentDetailResponse(Long paymentId,
  Long orderId,              // 주문 상세 화면 이동용
  PaymentStatus status,
  Integer amount,
  String method,             // 결제수단 (토스 응답값)
  OffsetDateTime approvedAt,
  String receiptUrl,         // 영수증 링크
  String approvalNumber,     // 카드사 승인 번호. 카드 결제가 아니거나 승인 전이면 null
  Integer cancelAmount,      // 환불 처리 중 · 환불 완료일 때 취소 금액 (#127). 그 밖에는 null
  String cancelReason,       // 취소 사유 (#127)
  OffsetDateTime canceledAt) { // 환불 완료 시각 (#127). 환불 처리 중이면 null

  public static PaymentDetailResponse from(Payment payment) {
    return from(payment, null);
  }

  // 환불 표시 (설계서 'API 명세' 5.4.1): 환불 처리 중 · 환불 완료면 가장 최근 취소 요청의 금액 · 사유 · 시각
  public static PaymentDetailResponse from(Payment payment, PaymentCancel cancel) {
    boolean refund = cancel != null
        && (payment.getStatus() == PaymentStatus.CANCEL_REQUESTED || payment.getStatus() == PaymentStatus.CANCELED);
    return new PaymentDetailResponse(
      payment.getId(),
      payment.getOrderId(),
      payment.getStatus(),
      payment.getAmount(),
      payment.getMethod(),
      payment.getApprovedAt(),
      payment.getReceiptUrl(),
      payment.getApprovalNumber(),
      refund ? cancel.getAmount() : null,
      refund ? cancel.getReason() : null,
      refund ? cancel.getCanceledAt() : null);
  }
}
