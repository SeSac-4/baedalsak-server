package com.baedalsak.delivery.controller;

import com.baedalsak.delivery.auth.CurrentMember;
import com.baedalsak.delivery.auth.LoginMember;
import com.baedalsak.delivery.dto.DeliveryCreateRequest;
import com.baedalsak.delivery.dto.DeliveryCreateResponse;
import com.baedalsak.delivery.dto.RiderDeliveryResponse;
import com.baedalsak.delivery.dto.WaitingDeliveryResponse;
import com.baedalsak.delivery.service.DeliveryAcceptService;
import com.baedalsak.delivery.service.DeliveryCreateService;
import com.baedalsak.delivery.service.DeliveryWaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 배달 API. gateway 가 /v1/deliveries 이하를 이 서비스로 보낸다.
 */
@RestController
@RequestMapping("/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryCreateService deliveryCreateService;
    private final DeliveryWaitingService deliveryWaitingService;
    private final DeliveryAcceptService deliveryAcceptService;

    // 6.1.1 배달 요청 생성 (WAITING). 같은 주문이 이미 있으면 409
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryCreateResponse create(@Valid @RequestBody DeliveryCreateRequest request) {
        return deliveryCreateService.create(request);
    }

    // 6.2.1 배차 대기 목록 (라이더). status=WAITING, 오래된 순. 인증·역할 검사는 gateway 담당
    @GetMapping("/waiting")
    public List<WaitingDeliveryResponse> getWaiting() {
        return deliveryWaitingService.getWaitingDeliveries();
    }

    // 6.2.2 배차 수락 (라이더). WAITING → ASSIGNED. 먼저 처리된 1명만 성공, 나머지 409 DELIVERY_ALREADY_ASSIGNED
    @PostMapping("/{deliveryId}/accept")
    public RiderDeliveryResponse accept(@PathVariable Long deliveryId, @CurrentMember LoginMember rider) {
        return deliveryAcceptService.accept(deliveryId, rider.id());
    }
}
