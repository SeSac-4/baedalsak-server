package com.baedalsak.order.controller;

import com.baedalsak.order.dto.response.ActiveOrderCountResponse;
import com.baedalsak.order.dto.response.OrderInternalResponse;
import com.baedalsak.order.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [내부] 주문 조회 API. gateway 라우트가 없어 외부에서는 호출할 수 없고(404), payment · store 가 K8s DNS 로 직접 호출한다.
 */
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderQueryService orderQueryService;

    // store 가 가게 폐업 전에 호출 (#176). 결제완료 · 조리중 · 배달중 주문 수
    @GetMapping("/active-count")
    public ActiveOrderCountResponse countActive(@RequestParam Long storeId) {
        return orderQueryService.countActiveByStore(storeId);
    }

    @GetMapping("/{orderId}")
    public OrderInternalResponse getOrder(@PathVariable Long orderId) {
        return orderQueryService.getInternal(orderId);
    }
}
