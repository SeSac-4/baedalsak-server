package com.baedalsak.order.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderItem;
import com.baedalsak.order.dto.response.OrderDetailResponse;
import com.baedalsak.order.dto.response.OrderInternalResponse;
import com.baedalsak.order.dto.response.OrderSummaryResponse;
import com.baedalsak.order.dto.response.PageResponse;
import com.baedalsak.order.error.OrderErrorCode;
import com.baedalsak.order.repository.OrderItemRepository;
import com.baedalsak.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 조회 (4.2.1). 자기 DB 의 스냅샷만으로 응답하고 다른 서비스를 호출하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // 내 주문 최신순. 한 페이지의 항목은 한 번에 조회한다
    public PageResponse<OrderSummaryResponse> getMyOrders(LoginMember member, int page, int size) {
        Page<Order> orders = orderRepository.findAllByCustomerIdOrderByCreatedAtDescIdDesc(
                member.id(), PageRequest.of(page, size));
        Map<Long, List<OrderItem>> items = orders.isEmpty() ? Map.of()
                : orderItemRepository.findAllByOrderIdInOrderById(orders.map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<OrderSummaryResponse> content = orders.stream()
                .map(order -> OrderSummaryResponse.of(order, items.getOrDefault(order.getId(), List.of())))
                .toList();
        return PageResponse.of(orders, content);
    }

    // 고객은 customer_id, 사장님은 owner_id 가 일치할 때만 (API 명세 35행)
    public OrderDetailResponse getOrder(LoginMember member, Long orderId) {
        Order order = getExistingOrder(orderId);
        boolean allowed = switch (member.role()) {
            case CUSTOMER -> order.isOrderedBy(member.id());
            case OWNER -> order.isOwnedBy(member.id());
            case RIDER -> false;
        };
        if (!allowed) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return OrderDetailResponse.of(order, orderItemRepository.findAllByOrderIdOrderById(orderId));
    }

    // [내부] payment 가 결제 요청 생성 때 호출
    public OrderInternalResponse getInternal(Long orderId) {
        return OrderInternalResponse.from(getExistingOrder(orderId));
    }

    private Order getExistingOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
