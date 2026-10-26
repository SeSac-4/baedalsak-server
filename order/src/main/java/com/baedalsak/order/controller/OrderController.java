package com.baedalsak.order.controller;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.order.dto.request.CreateOrderRequest;
import com.baedalsak.order.dto.response.OrderDetailResponse;
import com.baedalsak.order.dto.response.OrderSummaryResponse;
import com.baedalsak.order.dto.response.PageResponse;
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

    // 4.1.1 주문 생성 (장바구니 기준, 결제대기로 생성)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetailResponse create(@CurrentMember LoginMember member,
                                      @Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(member, request);
    }

    // 4.2.1 내 주문 목록 (최신순, page 는 0부터, 기본 size 20·최대 100)
    @GetMapping
    public PageResponse<OrderSummaryResponse> getMyOrders(@CurrentMember LoginMember member,
                                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderQueryService.getMyOrders(member, page, size);
    }

    // 4.2.1 주문 상세 (고객·사장님 본인만)
    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrder(@CurrentMember LoginMember member, @PathVariable Long orderId) {
        return orderQueryService.getOrder(member, orderId);
    }
}
