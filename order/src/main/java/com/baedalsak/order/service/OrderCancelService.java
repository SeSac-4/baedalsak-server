package com.baedalsak.order.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderStatus;
import com.baedalsak.order.dto.response.OrderDetailResponse;
import com.baedalsak.order.error.OrderErrorCode;
import com.baedalsak.order.event.CancelPaymentCommand;
import com.baedalsak.order.event.OutboxAppender;
import com.baedalsak.order.repository.OrderItemRepository;
import com.baedalsak.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 사장님 거절 · 고객 취소 (사가 결정 -3). 결제완료에서만 바뀌고, 상태 변경과 CancelPayment 를 한 트랜잭션에 묶는다.
 * 환불은 payment 가 뒤에서 진행하므로 토스 결과를 기다리지 않고 응답한다.
 * 조건부 UPDATE 라 수락과 겹쳐도 한쪽만 성공한다.
 */
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    static final String REJECT_REASON = "사장님 주문 거절";
    static final String CANCEL_REASON = "고객 주문 취소";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OutboxAppender outboxAppender;
    private final Clock clock;

    @Transactional
    public OrderDetailResponse reject(LoginMember member, Long orderId) {
        if (member.role() != MemberRole.OWNER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(member.id())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return refund(orderId, OrderStatus.REJECTED, REJECT_REASON, CancelPaymentCommand.REQUESTED_BY_OWNER);
    }

    @Transactional
    public OrderDetailResponse cancel(LoginMember member, Long orderId) {
        orderRepository.findById(orderId)
                .filter(order -> order.isOrderedBy(member.id()))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return refund(orderId, OrderStatus.CANCELED, CANCEL_REASON, CancelPaymentCommand.REQUESTED_BY_CUSTOMER);
    }

    // 결제완료가 아니면(수락됨 · 이미 처리됨 · 결제 전) 409. payment 는 orderId 로 결제를 찾으므로 paymentId 는 보내지 않는다
    private OrderDetailResponse refund(Long orderId, OrderStatus to, String reason, String requestedBy) {
        if (orderRepository.changeStatus(orderId, OrderStatus.PAID, to, OffsetDateTime.now(clock)) == 0) {
            throw new BusinessException(OrderErrorCode.ORDER_INVALID_STATUS);
        }
        outboxAppender.append(CancelPaymentCommand.EVENT_TYPE, CancelPaymentCommand.ROUTING_KEY, orderId,
                new CancelPaymentCommand(orderId, null, reason, requestedBy));
        Order order = orderRepository.findById(orderId).orElseThrow();
        return OrderDetailResponse.of(order, orderItemRepository.findAllByOrderIdOrderById(orderId));
    }
}
