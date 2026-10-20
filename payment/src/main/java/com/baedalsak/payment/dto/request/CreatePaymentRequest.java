package com.baedalsak.payment.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
  @NotNull Long orderId
) {

}
