package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.domain.Delivery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * POST /v1/deliveries 요청 (6.1.1). 필드는 OrderAccepted 페이로드와 같다.
 * 길이 제한은 delivery 테이블 컬럼 크기 (V1__delivery_schema.sql).
 */
public record DeliveryCreateRequest(
        @NotNull Long orderId,
        @NotNull Long customerId,
        @NotBlank @Size(max = 20) String customerPhone,
        @NotNull Long ownerId,
        @NotNull Long storeId,
        @NotBlank @Size(max = 100) String storeName,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotBlank @Size(max = 50) String deliveryDong,
        @NotNull @PositiveOrZero Integer deliveryFee,
        @Size(max = 200) String riderRequest,
        @NotNull OffsetDateTime estimatedReadyAt
) {
    public Delivery toEntity() {
        return Delivery.builder()
                .orderId(orderId)
                .customerId(customerId)
                .ownerId(ownerId)
                .storeId(storeId)
                .storeName(storeName)
                .pickupAddress(pickupAddress)
                .deliveryAddress(deliveryAddress)
                .deliveryDong(deliveryDong)
                .customerPhone(customerPhone)
                .riderRequest(riderRequest)
                .deliveryFee(deliveryFee)
                .estimatedReadyAt(estimatedReadyAt)
                .build();
    }
}
