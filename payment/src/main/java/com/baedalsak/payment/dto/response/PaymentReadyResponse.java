package com.baedalsak.payment.dto.response;

public record PaymentReadyResponse(
  Long paymentId,       // 결제 상세 화면 이동용
  String tossOrderId,   // 토스 orderId: 6~64자, 영문·숫자·-·_
  String orderName,     // 토스 orderName: 최대 100자
  Integer amount
) {

}
