package com.baedalsak.payment.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.payment.dto.request.ExpirePaymentRequest;
import com.baedalsak.payment.dto.request.InternalConfirmPaymentRequest;
import com.baedalsak.payment.dto.request.InternalCreatePaymentRequest;
import com.baedalsak.payment.dto.response.ExpireResultResponse;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.dto.response.PaymentStatusResponse;
import com.baedalsak.payment.service.PaymentConfirmService;
import com.baedalsak.payment.service.PaymentInternalService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * payment [내부] API (#128, 설계서 'API 명세' 5.1.1 · 5.2.1 · 결제 상태 조회 · 만료).
 * 주문 · 결제 오케스트레이션 사가의 조정자(order)만 부른다. gateway 라우트가 없어 외부에서 닿지 않는다(/internal/** 404).
 */
@Validated
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentInternalController {

  private final PaymentInternalService paymentInternalService;
  private final PaymentConfirmService paymentConfirmService;

  // 결제 요청 생성 (주문 생성 직후). 같은 주문이면 기존 결제 요청을 돌려준다
  @PostMapping
  public PaymentReadyResponse create(@Valid @RequestBody InternalCreatePaymentRequest request) {
    return paymentInternalService.create(request);
  }

  // 결제 승인 (고객의 결제 확인을 조정자가 받아 지시). 이미 승인이면 409 PAYMENT_ALREADY_APPROVED → 조정자는 성공 처리
  @PostMapping("/confirm")
  public PaymentDetailResponse confirm(@Valid @RequestBody InternalConfirmPaymentRequest request) {
    return paymentConfirmService.confirmInternal(request);
  }

  // 결제 상태 일괄 조회 (조정자 점검). ?orderIds=1,2,3
  @GetMapping
  public List<PaymentStatusResponse> findStatuses(
      @RequestParam @NotEmpty @Size(max = 100) List<@NotNull Long> orderIds) {
    return paymentInternalService.findStatuses(orderIds);
  }

  // 결제 요청 만료 (조정자 점검, 결제대기 45분). 키 있는 결제는 토스 조회
  @PostMapping("/expire")
  public List<ExpireResultResponse> expire(@Valid @RequestBody ExpirePaymentRequest request) {
    return paymentInternalService.expire(request.orderIds());
  }
}
