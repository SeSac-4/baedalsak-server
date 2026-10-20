package com.baedalsak.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 결제 승인 요청 (엑셀 U-06: 토스 successUrl 로 받은 값을 그대로 전달)
// 토스 결제 승인 API(POST /v1/payments/confirm) 요청 본문과 같은 필드
public record ConfirmPaymentRequest(
  @NotBlank
  @Size(max = 200)                          // 토스: 최대 200자
  String paymentKey,

  @NotBlank
  @Size(min = 6, max = 64)                  // 토스: 6~64자
  @Pattern (regexp = "^[A-Za-z0-9_-]+$")     // 토스: 영문·숫자·-·_
  String orderId,                           // = payment.toss_order_id

  @NotNull 
  Integer amount
) {

}
