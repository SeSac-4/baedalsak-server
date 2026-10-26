package com.baedalsak.order.event;

import com.baedalsak.common.logging.MaskingUtils;
import com.baedalsak.order.domain.Order;

import java.time.OffsetDateTime;

/**
 * OrderAccepted 이벤트 본문 (라우팅 키 order.accepted, 설계서 '이벤트 목록' 탭 F6).
 * delivery 는 이 값으로 배달을 만들고(6.1.1), order 메일 큐는 고객에게 조리 시작 메일을 보낸다 (7.1.1).
 */
public record OrderAcceptedPayload(
        Long orderId,
        Long customerId,
        String customerPhone,
        Long ownerId,
        Long storeId,
        String storeName,
        String pickupAddress,
        String deliveryAddress,
        String deliveryDong,
        Integer deliveryFee,
        String riderRequest,
        OffsetDateTime estimatedReadyAt,
        String customerEmail,
        String ownerEmail
) {

    public static final String EVENT_TYPE = "OrderAccepted";
    public static final String ROUTING_KEY = "order.accepted";

    public static OrderAcceptedPayload from(Order order) {
        return new OrderAcceptedPayload(order.getId(), order.getCustomerId(), order.getCustomerPhone(),
                order.getOwnerId(), order.getStoreId(), order.getStoreName(), order.getPickupAddress(),
                order.getDeliveryAddress(), order.getDeliveryDong(), order.getDeliveryFee(), order.getRiderRequest(),
                order.getEstimatedReadyAt(), order.getCustomerEmail(), order.getOwnerEmail());
    }

    // 로그에 휴대폰 번호가 그대로 남지 않게 한다 (N4.4)
    @Override
    public String toString() {
        return "OrderAcceptedPayload[orderId=" + orderId + ", storeId=" + storeId + ", customerPhone="
                + MaskingUtils.maskPhone(customerPhone) + "]";
    }
}
