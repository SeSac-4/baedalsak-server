package com.baedalsak.order.controller;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.order.dto.request.AcceptOrderRequest;
import com.baedalsak.order.dto.request.ConfirmOrderPaymentRequest;
import com.baedalsak.order.dto.request.CreateOrderRequest;
import com.baedalsak.order.dto.response.OrderCreatedResponse;
import com.baedalsak.order.dto.response.OrderDetailResponse;
import com.baedalsak.order.dto.response.OrderSummaryResponse;
import com.baedalsak.order.dto.response.PageResponse;
import com.baedalsak.order.service.OrderAcceptService;
import com.baedalsak.order.service.OrderCancelService;
import com.baedalsak.order.service.OrderPaymentService;
import com.baedalsak.order.service.OrderQueryService;
import com.baedalsak.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 API. gateway 가 /v1/orders 이하를 이 서비스로 보낸다.
 */
@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final OrderAcceptService orderAcceptService;
    private final OrderCancelService orderCancelService;
    private final OrderPaymentService orderPaymentService;

    // 4.1.1 주문 생성 (장바구니 기준, 결제대기로 생성). 결제 요청까지 만들어 토스 결제창 값을 함께 준다
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreatedResponse create(@CurrentMember LoginMember member,
                                      @Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(member, request);
    }

    // 결제 확인 (U-06). 가게·메뉴 재확인 → payment 승인 → 결제완료. 결과를 모르면 502 (같은 paymentKey 로 다시 시도)
    @PostMapping("/{orderId}/payment/confirm")
    public OrderDetailResponse confirmPayment(@CurrentMember LoginMember member, @PathVariable Long orderId,
                                              @Valid @RequestBody ConfirmOrderPaymentRequest request) {
        return orderPaymentService.confirm(member, orderId, request);
    }

    // 4.2.1 내 주문 목록 (최신순, page 는 0부터, 기본 size 20·최대 100)
    @GetMapping
    public PageResponse<OrderSummaryResponse> getMyOrders(@CurrentMember LoginMember member,
                                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderQueryService.getMyOrders(member, page, size);
    }

    // 4.3.2 주문 수락 (사장님). 결제완료 → 조리중, 이미 처리된 주문이면 409 ORDER_INVALID_STATUS
    @PostMapping("/{orderId}/accept")
    public OrderDetailResponse accept(@CurrentMember LoginMember member, @PathVariable Long orderId,
                                      @Valid @RequestBody AcceptOrderRequest request) {
        return orderAcceptService.accept(member, orderId, request);
    }

    // 주문 거절 (사장님). 결제완료 → 주문거절, 환불은 뒤에서 진행. 이미 처리된 주문이면 409 ORDER_INVALID_STATUS
    @PostMapping("/{orderId}/reject")
    public OrderDetailResponse reject(@CurrentMember LoginMember member, @PathVariable Long orderId) {
        return orderCancelService.reject(member, orderId);
    }

    // 주문 취소 (고객, 수락 전). 결제완료 → 주문취소, 환불은 뒤에서 진행. 이미 수락됐으면 409 ORDER_INVALID_STATUS
    @PostMapping("/{orderId}/cancel")
    public OrderDetailResponse cancel(@CurrentMember LoginMember member, @PathVariable Long orderId) {
        return orderCancelService.cancel(member, orderId);
    }

    // 4.2.1 주문 상세 (고객·사장님 본인만)
    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrder(@CurrentMember LoginMember member, @PathVariable Long orderId) {
        return orderQueryService.getOrder(member, orderId);
    }
}
