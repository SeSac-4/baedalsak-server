package com.baedalsak.order.service;

import com.baedalsak.order.domain.Cart;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderItem;
import com.baedalsak.order.domain.OrderStatus;
import com.baedalsak.order.event.CancelPaymentCommand;
import com.baedalsak.order.event.OutboxAppender;
import com.baedalsak.order.repository.CartRepository;
import com.baedalsak.order.repository.OrderItemRepository;
import com.baedalsak.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 결제 결과에 따른 주문 상태 변경. 결제 확인 API·PaymentApproved 수신이 같은 규칙을 쓴다 (사가 결정 1-1 #6).
 * 조건부 UPDATE 라 경로가 겹쳐도 한 번만 반영된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentTransitions {

    // 평소 지연·짧은 장애는 결제완료, 그 이상은 늦은 주문으로 환불
    static final Duration LATE_APPROVAL_LIMIT = Duration.ofMinutes(10);
    static final String LATE_APPROVAL_REASON = "결제 확인이 늦어져 자동 환불";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final OutboxAppender outboxAppender;
    private final Clock clock;

    // 결제대기 → 결제완료 + 주문에 들어간 항목만 장바구니에서 삭제. 이미 바뀌었으면 false
    @Transactional
    public boolean markPaid(Order order) {
        boolean paid = orderRepository.markPaid(order.getId(), order.getTotalAmount(), now(),
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID) == 1;
        if (paid) {
            log.info("order paid: orderId={}", order.getId());
            clearOrderedItems(order);
        }
        return paid;
    }

    // 결제대기 → 결제만료. 이미 바뀌었으면 false
    @Transactional
    public boolean expire(Long orderId) {
        return orderRepository.changeStatus(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_EXPIRED,
                now()) == 1;
    }

    // 늦게 발견한 승인: 승인 후 10분 이내면 결제완료, 넘으면 결제만료 + 자동 환불
    @Transactional
    public void applyApproval(Order order, Long paymentId, OffsetDateTime approvedAt) {
        if (!now().isAfter(approvedAt.plus(LATE_APPROVAL_LIMIT))) {
            markPaid(order);
            return;
        }
        if (expire(order.getId())) {
            requestRefund(order.getId(), paymentId);
        }
    }

    // 같은 트랜잭션에 CancelPayment 명령을 남긴다. 다시 보내도 payment 가 한 번만 취소한다
    @Transactional
    public void requestRefund(Long orderId, Long paymentId) {
        log.warn("order refund requested: orderId={}, paymentId={}", orderId, paymentId);
        outboxAppender.append(CancelPaymentCommand.EVENT_TYPE, CancelPaymentCommand.ROUTING_KEY, orderId,
                new CancelPaymentCommand(orderId, paymentId, LATE_APPROVAL_REASON,
                        CancelPaymentCommand.REQUESTED_BY_SYSTEM));
    }

    // 항목이 남지 않으면 장바구니도 지운다 (빈 장바구니에 가게 정보만 남지 않게)
    private void clearOrderedItems(Order order) {
        cartRepository.findCart(order.getCustomerId()).ifPresent(cart -> {
            List<Long> menuIds = orderItemRepository.findAllByOrderIdOrderById(order.getId()).stream()
                    .map(OrderItem::getMenuId)
                    .toList();
            cartRepository.deleteItems(cart.getId(), menuIds);
            deleteIfEmpty(cart);
        });
    }

    private void deleteIfEmpty(Cart cart) {
        if (cartRepository.findItems(cart.getId()).isEmpty()) {
            cartRepository.deleteCart(cart);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
