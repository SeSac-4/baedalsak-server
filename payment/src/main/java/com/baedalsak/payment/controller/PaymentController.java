package com.baedalsak.payment.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.payment.dto.request.ConfirmPaymentRequest;
import com.baedalsak.payment.dto.request.CreatePaymentRequest;
import com.baedalsak.payment.dto.response.PageResponse;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.dto.response.PaymentSummaryResponse;
import com.baedalsak.payment.service.PaymentConfirmService;
import com.baedalsak.payment.service.PaymentQueryService;
import com.baedalsak.payment.service.PaymentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 결제 API. gateway 가 /v1/payments 이하를 이 서비스로 보낸다.
 */
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;
  private final PaymentConfirmService paymentConfirmService;
  private final PaymentQueryService paymentQueryService;

  // 결제 요청 생성 (U-05 진입). 같은 주문이면 기존 결제 요청을 돌려준다
  @PostMapping
  public PaymentReadyResponse request(@CurrentMember LoginMember member,
      @Valid @RequestBody CreatePaymentRequest request) {
    return paymentService.request(member, request.orderId());
  }

  // 결제 승인 (U-06, 토스 successUrl 복귀 후). 재호출은 409 PAYMENT_ALREADY_APPROVED
  @PostMapping("/confirm")
  public PaymentDetailResponse confirm(@CurrentMember LoginMember member,
      @Valid @RequestBody ConfirmPaymentRequest request) {
    return paymentConfirmService.confirm(member, request);
  }

  // 5.4.2 내 결제 목록 (U-10, 최신순, page 는 0부터, 기본 size 20·최대 100)
  @GetMapping
  public PageResponse<PaymentSummaryResponse> getMyPayments(@CurrentMember LoginMember member,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return paymentQueryService.getMyPayments(member, page, size);
  }

  // 5.4.1 결제 상세 (U-11, 본인 결제만)
  @GetMapping("/{paymentId}")
  public PaymentDetailResponse getPayment(@CurrentMember LoginMember member, @PathVariable Long paymentId) {
    return paymentQueryService.getPayment(member, paymentId);
  }

  // 주문 상세(U-09)의 결제 상세 링크용. 주문 ID 로 결제를 찾는다 (본인 결제만)
  @GetMapping("/by-order/{orderId}")
  public PaymentDetailResponse getPaymentByOrder(@CurrentMember LoginMember member, @PathVariable Long orderId) {
    return paymentQueryService.getPaymentByOrder(member, orderId);
  }
}
