package com.baedalsak.payment.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * [내부] 결제 요청 만료 (#128). 조정자 점검이 45분 지난 결제대기 주문을 보낸다.
 * 키가 있는 결제는 토스 조회(1건 최대 7초)를 하므로 조정자는 1건씩 보낸다
 */
public record ExpirePaymentRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> orderIds
) {
}
