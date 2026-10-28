package com.baedalsak.delivery.service;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.Outbox;
import com.baedalsak.delivery.dto.RiderDeliveryResponse;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.payload.DeliveryPickedUpEvent;
import com.baedalsak.delivery.repository.DeliveryRepository;
import com.baedalsak.delivery.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 6.3.1 픽업 완료. 배정된 라이더가 가게에서 음식을 받아 ASSIGNED → PICKED_UP 으로 바꾼다.
 * 주문을 배달중으로 바꾸는 건 DeliveryPickedUp 을 구독한 order 가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryPickupService {

    private final DeliveryRepository deliveryRepository;
    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    // 배달 UPDATE 와 outbox INSERT 를 한 트랜잭션에 → 픽업됐는데 이벤트가 빠지는 일이 없다 (docs 3-2)
    @Transactional
    public RiderDeliveryResponse pickUp(Long deliveryId, Long riderId) {
        if (deliveryRepository.pickUpIfAssigned(deliveryId, riderId, OffsetDateTime.now()) == 0) {
            throw rejectReason(deliveryId, riderId);
        }

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        outboxRepository.save(Outbox.of(
                DeliveryPickedUpEvent.TYPE,
                DeliveryPickedUpEvent.ROUTING_KEY,
                delivery.getId(),
                jsonMapper.writeValueAsString(DeliveryPickedUpEvent.from(delivery)),
                MDC.get("traceId")));

        log.info("delivery picked up: deliveryId={}, orderId={}, riderId={}",
                delivery.getId(), delivery.getOrderId(), riderId);
        return RiderDeliveryResponse.withoutCustomerPhone(delivery);
    }

    // 0행: 없는 배달(404) / 내 배달 아님(403) / ASSIGNED 가 아님(409, 이미 픽업한 경우 포함)
    private BusinessException rejectReason(Long deliveryId, Long riderId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        if (!Objects.equals(delivery.getRiderId(), riderId)) {
            return new BusinessException(ErrorCode.FORBIDDEN, "본인에게 배정된 배달만 픽업할 수 있습니다.");
        }
        log.info("pickup rejected by status: deliveryId={}, status={}", deliveryId, delivery.getStatus());
        return new BusinessException(ErrorCode.DELIVERY_INVALID_STATUS);
    }
}
