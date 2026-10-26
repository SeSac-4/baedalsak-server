package com.baedalsak.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.payment.dto.request.CreatePaymentRequest;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 결제 API. gateway 가 /v1/payments 이하를 이 서비스로 보낸다.
 */
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  // 결제 요청 생성 (U-05 진입). 같은 주문이면 기존 결제 요청을 돌려준다
  @PostMapping
  public PaymentReadyResponse request(@CurrentMember LoginMember member,
      @Valid @RequestBody CreatePaymentRequest request) {
    return paymentService.request(member, request.orderId());
  }
}
