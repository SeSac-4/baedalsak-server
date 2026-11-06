package com.baedalsak.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 결제 확인 요청 (POST /v1/orders/{orderId}/payment/confirm). 토스 successUrl 로 받은 값을 그대로 보낸다 (화면 U-06).
 */
public record ConfirmOrderPaymentRequest(
        @NotBlank(message = "결제 키가 없습니다.")
        @Size(max = 200, message = "결제 키는 200자 이하입니다.")   // 토스: 최대 200자
        String paymentKey,

        @NotNull(message = "결제 금액이 없습니다.")
        Integer amount
) {
}
