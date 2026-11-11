package com.baedalsak.order.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderItem;
import com.baedalsak.order.domain.OrderStatus;
import com.baedalsak.order.dto.response.ActiveOrderCountResponse;
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

    // 결제가 끝나지 않았거나 만료된 주문은 아직 주문이 아니다. 고객 주문 내역과 사장님 목록에 보이지 않는다
    private static final EnumSet<OrderStatus> UNPAID =
            EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_EXPIRED);

    // 가게 폐업을 막는 진행 중 상태 (#176)
    private static final EnumSet<OrderStatus> ACTIVE_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.COOKING, OrderStatus.DELIVERING);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // 내 주문 최신순. 결제대기·결제만료는 노출하지 않는다. 한 페이지의 항목은 한 번에 조회한다
    public PageResponse<OrderSummaryResponse> getMyOrders(LoginMember member, int page, int size) {
        Page<Order> orders = orderRepository.findAllByCustomerIdAndStatusNotInOrderByCreatedAtDescIdDesc(
                member.id(), UNPAID, PageRequest.of(page, size));
        Map<Long, List<OrderItem>> items = itemsOf(orders);
        List<OrderSummaryResponse> content = orders.stream()
                .map(order -> OrderSummaryResponse.of(order, items.getOrDefault(order.getId(), List.of())))
                .toList();
        return PageResponse.of(orders, content);
    }

    // 사장님 가게 주문 목록 (4.3.1). 결제대기·결제만료는 노출하지 않는다. 상태를 안 주면 나머지 전부
    public PageResponse<OrderDetailResponse> getStoreOrders(LoginMember member, Long storeId,
                                                            List<OrderStatus> statuses, int page, int size) {
        if (member.role() != MemberRole.OWNER || orderRepository.existsByStoreIdAndOwnerIdNot(storeId, member.id())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        EnumSet<OrderStatus> visible = statuses == null || statuses.isEmpty()
                ? EnumSet.complementOf(UNPAID)
                : EnumSet.copyOf(statuses);
        visible.removeAll(UNPAID);
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

    // 고객은 customer_id, 사장님은 owner_id 가 일치할 때만 (API 명세 35행). 고객에게 결제대기·결제만료는 없는 주문 (U-09)
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
        if (member.role() == MemberRole.CUSTOMER && UNPAID.contains(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return OrderDetailResponse.of(order, orderItemRepository.findAllByOrderIdOrderById(orderId));
    }

    // [내부] payment 가 결제 요청 생성 때 호출
    public OrderInternalResponse getInternal(Long orderId) {
        return OrderInternalResponse.of(getExistingOrder(orderId),
                orderItemRepository.findAllByOrderIdOrderById(orderId));
    }

    // [내부] 가게의 진행 중 주문 수. store 가 폐업 전에 확인한다 (#176)
    //   결제대기는 빼는데, 폐업 후 결제 확정 시 승인 직전 재확인에서 STORE_NOT_FOUND 로 실패해 돈이 나가지 않기 때문
    public ActiveOrderCountResponse countActiveByStore(Long storeId) {
        return new ActiveOrderCountResponse(orderRepository.countByStoreIdAndStatusIn(storeId, ACTIVE_STATUSES));
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
