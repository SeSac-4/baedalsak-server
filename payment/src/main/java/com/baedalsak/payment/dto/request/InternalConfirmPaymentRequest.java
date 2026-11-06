package com.baedalsak.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * [내부] 결제 승인 (#128, 설계서 'API 명세' 5.2.1). 조정자(order)가 고객의 결제 확인을 받아 보낸다.
 * orderId 는 DB 주문 ID — payment 가 order_id 로 결제를 찾아 저장된 토스 주문번호로 승인한다.
 */
public record InternalConfirmPaymentRequest(
    @NotNull Long orderId,
    @NotNull Long customerId,
    @NotBlank @Size(max = 200) String paymentKey,       // 토스: 최대 200자
    @NotNull Integer amount
) {
}
