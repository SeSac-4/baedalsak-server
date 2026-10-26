package com.baedalsak.order.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderItem;
import com.baedalsak.order.domain.OrderStatus;
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

import java.util.EnumSet;
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
        Map<Long, List<OrderItem>> items = itemsOf(orders);
        List<OrderSummaryResponse> content = orders.stream()
                .map(order -> OrderSummaryResponse.of(order, items.getOrDefault(order.getId(), List.of())))
                .toList();
        return PageResponse.of(orders, content);
    }

    // 사장님 가게 주문 목록 (4.3.1). 결제대기(PENDING_PAYMENT)는 노출하지 않는다. 상태를 안 주면 나머지 전부
    public PageResponse<OrderDetailResponse> getStoreOrders(LoginMember member, Long storeId,
                                                            List<OrderStatus> statuses, int page, int size) {
        if (member.role() != MemberRole.OWNER || orderRepository.existsByStoreIdAndOwnerIdNot(storeId, member.id())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        EnumSet<OrderStatus> visible = statuses == null || statuses.isEmpty()
                ? EnumSet.complementOf(EnumSet.of(OrderStatus.PENDING_PAYMENT))
                : EnumSet.copyOf(statuses);
        visible.remove(OrderStatus.PENDING_PAYMENT);
        if (visible.isEmpty()) {
            return PageResponse.of(Page.empty(PageRequest.of(page, size)), List.of());
        }
        Page<Order> orders = orderRepository.findAllByStoreIdAndOwnerIdAndStatusInOrderByCreatedAtDescIdDesc(
                storeId, member.id(), visible, PageRequest.of(page, size));
        Map<Long, List<OrderItem>> items = itemsOf(orders);
        List<OrderDetailResponse> content = orders.stream()
                .map(order -> OrderDetailResponse.of(order, items.getOrDefault(order.getId(), List.of())))
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

    // 한 페이지의 주문 항목을 한 번에 조회
    private Map<Long, List<OrderItem>> itemsOf(Page<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        return orderItemRepository.findAllByOrderIdInOrderById(orders.map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    private Order getExistingOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
