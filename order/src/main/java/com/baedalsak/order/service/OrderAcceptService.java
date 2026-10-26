package com.baedalsak.order.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderStatus;
import com.baedalsak.order.dto.request.AcceptOrderRequest;
import com.baedalsak.order.dto.response.OrderDetailResponse;
import com.baedalsak.order.error.OrderErrorCode;
import com.baedalsak.order.event.OrderAcceptedPayload;
import com.baedalsak.order.event.OutboxAppender;
import com.baedalsak.order.repository.OrderItemRepository;
import com.baedalsak.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 주문 수락 (4.3.2). 결제완료 → 조리중, 조리 완료 예상 시각 = 지금 + 조리 예상 분.
 * 상태 변경과 OrderAccepted outbox 저장을 한 트랜잭션에 묶는다 → 배달 생성은 이벤트로 비동기(수초) (API 명세 46행).
 */
@Service
@RequiredArgsConstructor
public class OrderAcceptService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OutboxAppender outboxAppender;
    private final Clock clock;

    @Transactional
    public OrderDetailResponse accept(LoginMember member, Long orderId, AcceptOrderRequest request) {
        if (member.role() != MemberRole.OWNER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = orderRepository.accept(orderId, member.id(), now.plusMinutes(request.estimatedMinutes()), now,
                OrderStatus.PAID, OrderStatus.COOKING);
        if (updated == 0) {
            throw rejected(member, orderId);
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        outboxAppender.append(OrderAcceptedPayload.EVENT_TYPE, OrderAcceptedPayload.ROUTING_KEY, order.getId(),
                OrderAcceptedPayload.from(order));
        return OrderDetailResponse.of(order, orderItemRepository.findAllByOrderIdOrderById(orderId));
    }

    // 바뀐 행이 없을 때 이유: 없는 주문 404, 남의 가게 403, 결제완료가 아님(이미 수락·결제 전) 409
    private BusinessException rejected(LoginMember member, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(member.id())) {
            return new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return new BusinessException(OrderErrorCode.ORDER_INVALID_STATUS);
    }
}
