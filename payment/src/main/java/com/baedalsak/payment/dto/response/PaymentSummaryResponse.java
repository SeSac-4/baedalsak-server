package com.baedalsak.payment.dto.response;

import java.time.OffsetDateTime;

import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;

public record PaymentSummaryResponse(     
  Long paymentId,            // 결제 상세 화면 이동용
  PaymentStatus status,
  Integer amount,
  OffsetDateTime approvedAt) {
  public static PaymentSummaryResponse from(Payment payment) {
    return new PaymentSummaryResponse(
      payment.getId(),
      payment.getStatus(),
      payment.getAmount(),
      payment.getApprovedAt());
  }
}
