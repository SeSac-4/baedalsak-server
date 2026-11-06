package com.baedalsak.payment.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.payment.dto.response.PageResponse;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.dto.response.PaymentSummaryResponse;
import com.baedalsak.payment.service.PaymentQueryService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 결제 조회 API (고객). gateway 가 /v1/payments 이하를 이 서비스로 보낸다.
 * 결제 요청 생성 · 승인은 조정자(order)가 내부 API(PaymentInternalController)로 지시한다 (#128). 고객 생성 · 승인 API 는 #129 에서 삭제
 */
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentQueryService paymentQueryService;

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
