package com.baedalsak.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 주문 수락 요청 (POST /v1/orders/{orderId}/accept). 조리 예상 시간(분) — 화면 O-02 칩 10·15·20·30·45·60분 + 직접 입력
 */
public record AcceptOrderRequest(
        @NotNull(message = "조리 예상 시간을 입력해 주세요.")
        @Positive(message = "조리 예상 시간은 1분 이상이어야 합니다.")
        Integer estimatedMinutes
) {
}
