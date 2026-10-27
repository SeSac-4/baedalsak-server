package com.baedalsak.payment.dto.response;

import java.time.OffsetDateTime;

import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;

// 내 결제 목록 항목 (설계서 '화면 흐름' U-10: 가게명·금액·상태·승인 시각)
public record PaymentSummaryResponse(
  Long paymentId,            // 결제 상세 화면 이동용
  String storeName,          // 결제 요청 생성 시 스냅샷
  PaymentStatus status,
  Integer amount,
  OffsetDateTime approvedAt) {  // 결제 요청됨이면 null

  public static PaymentSummaryResponse from(Payment payment) {
    return new PaymentSummaryResponse(
      payment.getId(),
      payment.getStoreName(),
      payment.getStatus(),
      payment.getAmount(),
      payment.getApprovedAt());
  }
}
