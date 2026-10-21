package com.baedalsak.delivery.service;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.dto.DeliveryCreateRequest;
import com.baedalsak.delivery.dto.DeliveryCreateResponse;
import com.baedalsak.delivery.exception.DeliveryAlreadyExistsException;
import com.baedalsak.delivery.payload.OrderAcceptedEvent;
import com.baedalsak.delivery.repository.DeliveryRepository;
import com.baedalsak.delivery.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 6.1.1 배달 요청 생성. 생성된 요청은 배차 대기(WAITING) 상태.
 * 입구는 두 개: OrderAccepted 소비(OrderAcceptedListener), POST /v1/deliveries(DeliveryController).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryCreateService {

    private final DeliveryRepository deliveryRepository;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * 이벤트 소비. processed_event INSERT 와 delivery INSERT 를 한 트랜잭션에 넣어,
     * 같은 이벤트가 두 번 와도 배달은 1건만 생긴다 (N2.4).
     */
    @Transactional
    public void createFromOrderAccepted(UUID eventId, OrderAcceptedEvent event) {
        if (processedEventRepository.insertIfAbsent(eventId, OrderAcceptedEvent.TYPE) == 0) {
            log.info("OrderAccepted already processed: eventId={}, orderId={}", eventId, event.orderId());
            return;
        }
        // 다른 eventId 로 같은 주문이 다시 와도 order_id UNIQUE 로 1건만 (설계서 '상태 전이': 이미 처리됨으로 ack)
        if (deliveryRepository.existsByOrderId(event.orderId())) {
            log.info("delivery already exists: eventId={}, orderId={}", eventId, event.orderId());
            return;
        }

        Delivery delivery = deliveryRepository.save(event.toEntity());
        log.info("delivery created: deliveryId={}, orderId={}", delivery.getId(), delivery.getOrderId());
    }

    /**
     * HTTP 요청. 이벤트와 달리 재전송을 조용히 넘기지 않고 409 로 알린다.
     */
    @Transactional
    public DeliveryCreateResponse create(DeliveryCreateRequest request) {
        if (deliveryRepository.existsByOrderId(request.orderId())) {
            throw new DeliveryAlreadyExistsException(request.orderId());
        }

        Delivery delivery = deliveryRepository.save(request.toEntity());
        log.info("delivery created via api: deliveryId={}, orderId={}", delivery.getId(), delivery.getOrderId());
        return DeliveryCreateResponse.from(delivery);
    }
}
