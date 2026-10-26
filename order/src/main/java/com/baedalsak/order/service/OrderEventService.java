package com.baedalsak.order.service;

import com.baedalsak.order.domain.OrderStatus;
import com.baedalsak.order.event.DeliveryAssignedEvent;
import com.baedalsak.order.event.DeliveryCompletedEvent;
import com.baedalsak.order.event.DeliveryPickedUpEvent;
import com.baedalsak.order.event.PaymentApprovedEvent;
import com.baedalsak.order.repository.OrderRepository;
import com.baedalsak.order.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * payment·delivery 이벤트 소비 → 주문 상태·라이더 반영 (설계서 '상태 전이' 탭).
 * processed_event INSERT 와 주문 UPDATE 를 한 트랜잭션에 넣어 같은 이벤트가 두 번 와도 한 번만 반영한다 (N2.4).
 * 현재 상태가 기대와 다르면(이미 반영됨·순서 뒤바뀜) 무시하고 ack — 재시도해도 결과가 같으므로 DLQ 로 보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventService {

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final Clock clock;

    // 결제대기 → 결제완료. 금액 불일치는 에러 로그 후 ack (상태 전이 탭 5행)
    @Transactional
    public void onPaymentApproved(UUID eventId, PaymentApprovedEvent event) {
        if (alreadyProcessed(eventId, PaymentApprovedEvent.TYPE, event.orderId())) {
            return;
        }
        if (orderRepository.markPaid(event.orderId(), event.amount(), now(),
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID) == 1) {
            log.info("order paid: orderId={}, paymentId={}", event.orderId(), event.paymentId());
            return;
        }
        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                log.error("PaymentApproved amount mismatch: eventId={}, orderId={}, orderAmount={}, paidAmount={}",
                        eventId, order.getId(), order.getTotalAmount(), event.amount());
            } else {
                log.info("PaymentApproved ignored: eventId={}, orderId={}, status={}",
                        eventId, order.getId(), order.getStatus());
            }
        }, () -> log.error("PaymentApproved for unknown order: eventId={}, orderId={}", eventId, event.orderId()));
    }

    @Transactional
    public void onDeliveryAssigned(UUID eventId, DeliveryAssignedEvent event) {
        if (alreadyProcessed(eventId, DeliveryAssignedEvent.TYPE, event.orderId())) {
            return;
        }
        if (orderRepository.assignRider(event.orderId(), event.riderName(), now()) == 0) {
            log.error("DeliveryAssigned for unknown order: eventId={}, orderId={}", eventId, event.orderId());
        }
    }

    // 조리중 → 배달중 (상태 전이 탭 7행)
    @Transactional
    public void onDeliveryPickedUp(UUID eventId, DeliveryPickedUpEvent event) {
        if (alreadyProcessed(eventId, DeliveryPickedUpEvent.TYPE, event.orderId())) {
            return;
        }
        changeStatus(eventId, DeliveryPickedUpEvent.TYPE, event.orderId(),
                OrderStatus.COOKING, OrderStatus.DELIVERING);
    }

    // 배달중 → 배달완료 (상태 전이 탭 8행)
    @Transactional
    public void onDeliveryCompleted(UUID eventId, DeliveryCompletedEvent event) {
        if (alreadyProcessed(eventId, DeliveryCompletedEvent.TYPE, event.orderId())) {
            return;
        }
        changeStatus(eventId, DeliveryCompletedEvent.TYPE, event.orderId(),
                OrderStatus.DELIVERING, OrderStatus.DELIVERED);
    }

    private void changeStatus(UUID eventId, String eventType, Long orderId, OrderStatus from, OrderStatus to) {
        if (orderRepository.changeStatus(orderId, from, to, now()) == 0) {
            log.info("{} ignored: eventId={}, orderId={}, expected={}", eventType, eventId, orderId, from);
        }
    }

    private boolean alreadyProcessed(UUID eventId, String eventType, Long orderId) {
        if (processedEventRepository.insertIfAbsent(eventId, eventType) == 0) {
            log.info("{} already processed: eventId={}, orderId={}", eventType, eventId, orderId);
            return true;
        }
        return false;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
