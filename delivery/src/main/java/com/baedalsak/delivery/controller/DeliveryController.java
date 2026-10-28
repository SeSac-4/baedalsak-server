package com.baedalsak.delivery.controller;

import com.baedalsak.delivery.auth.CurrentMember;
import com.baedalsak.delivery.auth.LoginMember;
import com.baedalsak.delivery.dto.DeliveryCreateRequest;
import com.baedalsak.delivery.dto.DeliveryCreateResponse;
import com.baedalsak.delivery.dto.DeliveryStatusResponse;
import com.baedalsak.delivery.dto.PageResponse;
import com.baedalsak.delivery.dto.RiderDeliveryResponse;
import com.baedalsak.delivery.dto.WaitingDeliveryResponse;
import com.baedalsak.delivery.service.DeliveryAcceptService;
import com.baedalsak.delivery.service.DeliveryActiveService;
import com.baedalsak.delivery.service.DeliveryCompleteService;
import com.baedalsak.delivery.service.DeliveryCreateService;
import com.baedalsak.delivery.service.DeliveryDetailService;
import com.baedalsak.delivery.service.DeliveryPickupService;
import com.baedalsak.delivery.service.DeliveryWaitingService;
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
 * 배달 API. gateway 가 /v1/deliveries 이하를 이 서비스로 보낸다.
 */
@RestController
@RequestMapping("/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryCreateService deliveryCreateService;
    private final DeliveryWaitingService deliveryWaitingService;
    private final DeliveryAcceptService deliveryAcceptService;
    private final DeliveryPickupService deliveryPickupService;
    private final DeliveryCompleteService deliveryCompleteService;
    private final DeliveryDetailService deliveryDetailService;
    private final DeliveryActiveService deliveryActiveService;

    // 6.1.1 배달 요청 생성 (WAITING). 같은 주문이 이미 있으면 409
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryCreateResponse create(@Valid @RequestBody DeliveryCreateRequest request) {
        return deliveryCreateService.create(request);
    }

    // 6.2.1 배차 대기 목록 (라이더). status=WAITING, 오래된 순 (page 는 0부터, 기본 size 20·최대 100). 인증·역할 검사는 gateway 담당
    @GetMapping("/waiting")
    public PageResponse<WaitingDeliveryResponse> getWaiting(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return deliveryWaitingService.getWaitingDeliveries(page, size);
    }

    // 6.2.2 배차 수락 (라이더). WAITING → ASSIGNED. 먼저 처리된 1명만 성공, 나머지 409 DELIVERY_ALREADY_ASSIGNED
    @PostMapping("/{deliveryId}/accept")
    public RiderDeliveryResponse accept(@PathVariable Long deliveryId, @CurrentMember LoginMember rider) {
        return deliveryAcceptService.accept(deliveryId, rider.id());
    }

    // 6.3.1 픽업 완료 (라이더). ASSIGNED → PICKED_UP. 배정된 라이더 본인만, 아니면 403
    @PostMapping("/{deliveryId}/pickup")
    public RiderDeliveryResponse pickUp(@PathVariable Long deliveryId, @CurrentMember LoginMember rider) {
        return deliveryPickupService.pickUp(deliveryId, rider.id());
    }

    // 6.3.2 배달 완료 (라이더). PICKED_UP → DELIVERED. 배정된 라이더 본인만, 아니면 403. 응답에 고객 연락처 없음
    @PostMapping("/{deliveryId}/complete")
    public DeliveryStatusResponse complete(@PathVariable Long deliveryId, @CurrentMember LoginMember rider) {
        return deliveryCompleteService.complete(deliveryId, rider.id());
    }

    // 6.4.1 주문의 배달 상태 (고객·사장님). customer_id 또는 owner_id 가 요청자일 때만, 아니면 403. 배달 생성 전이면 404
    @GetMapping("/by-order/{orderId}")
    public DeliveryStatusResponse getByOrder(@PathVariable Long orderId, @CurrentMember LoginMember member) {
        return deliveryDetailService.getByOrderId(orderId, member.id());
    }

    // 6.4.2 진행 중 배달 (라이더). status IN (ASSIGNED, PICKED_UP) 1건, 없으면 404. 고객 연락처는 이 기간에만 노출
    @GetMapping("/me/active")
    public RiderDeliveryResponse getActive(@CurrentMember LoginMember rider) {
        return deliveryActiveService.getActive(rider.id());
    }
}
