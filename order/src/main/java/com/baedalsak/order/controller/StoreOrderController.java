package com.baedalsak.order.controller;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.order.domain.OrderStatus;
import com.baedalsak.order.dto.response.OrderDetailResponse;
import com.baedalsak.order.dto.response.PageResponse;
import com.baedalsak.order.service.OrderQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사장님 가게 주문 목록 API. gateway 는 /v1/stores/{storeId}/orders 를 store 가 아니라 이 서비스로 먼저 보낸다 (docs 7번 라우팅 1순위).
 */
@RestController
@RequestMapping("/v1/stores/{storeId}/orders")
@RequiredArgsConstructor
public class StoreOrderController {

    private final OrderQueryService orderQueryService;

    // 4.3.1 신규(PAID)·진행 중(COOKING·DELIVERING)·완료(DELIVERED). 진행 중 탭은 ?status=COOKING&status=DELIVERING
    @GetMapping
    public PageResponse<OrderDetailResponse> getStoreOrders(@CurrentMember LoginMember member,
                                                            @PathVariable Long storeId,
                                                            @RequestParam(required = false) List<OrderStatus> status,
                                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderQueryService.getStoreOrders(member, storeId, status, page, size);
    }
}
