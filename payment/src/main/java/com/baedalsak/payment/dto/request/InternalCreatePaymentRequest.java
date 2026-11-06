package com.baedalsak.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * [내부] 결제 요청 생성 (#128, 설계서 'API 명세' 5.1.1). 조정자(order)가 주문 생성 직후 보낸다.
 * 금액 · 가게 · 수신자 이메일은 payment 에 스냅샷으로 저장, orderName 은 토스 결제창 주문명(저장하지 않고 응답에만)
 */
public record InternalCreatePaymentRequest(
    @NotNull Long orderId,
    @NotNull @Positive Integer amount,
    @NotNull Long customerId,
    @NotNull Long ownerId,
    @NotNull Long storeId,
    @NotBlank @Size(max = 100) String storeName,
    @NotBlank @Size(max = 255) String customerEmail,
    @NotBlank @Size(max = 255) String ownerEmail,
    @NotBlank @Size(max = 100) String orderName         // 토스 orderName: 최대 100자
) {
}
