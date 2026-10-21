package com.baedalsak.delivery.controller;

import com.baedalsak.delivery.dto.DeliveryCreateRequest;
import com.baedalsak.delivery.dto.DeliveryCreateResponse;
import com.baedalsak.delivery.service.DeliveryCreateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배달 API. gateway 가 /v1/deliveries 이하를 이 서비스로 보낸다.
 */
@RestController
@RequestMapping("/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryCreateService deliveryCreateService;

    // 6.1.1 배달 요청 생성 (WAITING). 같은 주문이 이미 있으면 409
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryCreateResponse create(@Valid @RequestBody DeliveryCreateRequest request) {
        return deliveryCreateService.create(request);
    }
}
