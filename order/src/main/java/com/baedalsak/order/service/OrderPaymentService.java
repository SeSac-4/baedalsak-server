package com.baedalsak.order.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.order.client.ConfirmPaymentCommand;
import com.baedalsak.order.client.MenuInternal;
import com.baedalsak.order.client.PaymentClient;
import com.baedalsak.order.client.PaymentExpireResult;
import com.baedalsak.order.client.StoreClient;
import com.baedalsak.order.client.StoreInternal;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderItem;
import com.baedalsak.order.domain.OrderStatus;
import com.baedalsak.order.dto.request.ConfirmOrderPaymentRequest;
import com.baedalsak.order.dto.response.OrderDetailResponse;
import com.baedalsak.order.error.OrderErrorCode;
import com.baedalsak.order.repository.OrderItemRepository;
import com.baedalsak.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 결제 확인 (조정자, 사가 결정 1-1 #3). 외부 호출은 트랜잭션 밖에서 하고, 상태 변경만 OrderPaymentTransitions 트랜잭션으로 묶는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StoreClient storeClient;
    private final PaymentClient paymentClient;
    private final OrderPaymentTransitions transitions;

    public OrderDetailResponse confirm(LoginMember member, Long orderId, ConfirmOrderPaymentRequest request) {
        Order order = orderRepository.findById(orderId)
                .filter(found -> found.isOrderedBy(member.id()))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderById(orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            // 재시도 전에 PaymentApproved 로 이미 결제완료됐으면 성공으로 응답
            return paidOrThrow(orderId, items, OrderErrorCode.ORDER_INVALID_STATUS);
        }
        Optional<ErrorCode> blocked = whyNotOrderable(order, items);
        if (blocked.isPresent()) {
            // 앞선 시도의 승인이 남아 있을 수 있어 payment 에 확인한다. 승인돼 있으면 10분 규칙을 따른다
            expire(order);
            return paidOrThrow(orderId, items, blocked.get());
        }
        paymentClient.confirm(new ConfirmPaymentCommand(orderId, member.id(), request.paymentKey(), request.amount()));
        transitions.markPaid(order);
        return paidOrThrow(orderId, items, OrderErrorCode.ORDER_INVALID_STATUS);
    }

    // payment 만료 호출 결과대로 주문을 정리한다. 호출이 실패하면 결제대기로 두고 점검이 정리한다
    public void expire(Order order) {
        PaymentExpireResult result;
        try {
            result = paymentClient.expire(order.getId());
        } catch (BusinessException e) {
            log.warn("payment 만료 호출 실패, 결제대기로 둠 orderId={}, code={}", order.getId(), e.getErrorCode().name());
            return;
        }
        switch (result.result()) {
            case EXPIRED, NONE -> transitions.expire(order.getId());
            case APPROVED -> transitions.applyApproval(order, null, result.approvedAt());
        }
    }

    // 지금 상태가 결제완료 이후면 성공, 아니면 주어진 이유로 응답한다
    private OrderDetailResponse paidOrThrow(Long orderId, List<OrderItem> items, ErrorCode otherwise) {
        Order current = orderRepository.findById(orderId).orElseThrow();
        return switch (current.getStatus()) {
            case PAID, COOKING, DELIVERING, DELIVERED -> OrderDetailResponse.of(current, items);
            default -> throw new BusinessException(otherwise);
        };
    }

    // 승인 직전 재확인. store 가 응답하지 않으면 503 (주문은 그대로, 돈 안 나감)
    private Optional<ErrorCode> whyNotOrderable(Order order, List<OrderItem> items) {
        Optional<StoreInternal> store = storeClient.getStore(order.getStoreId());
        if (store.isEmpty()) {
            return Optional.of(OrderErrorCode.STORE_NOT_FOUND);
        }
        if (!store.get().isOpenNow()) {
            return Optional.of(OrderErrorCode.STORE_CLOSED);
        }
        Optional<List<MenuInternal>> found = storeClient.getMenus(order.getStoreId(),
                items.stream().map(OrderItem::getMenuId).toList());
        if (found.isEmpty()) {
            return Optional.of(OrderErrorCode.STORE_NOT_FOUND);
        }
        Map<Long, MenuInternal> menus = found.get().stream()
                .collect(Collectors.toMap(MenuInternal::id, Function.identity()));
        boolean allOnSale = items.stream().allMatch(item -> {
            MenuInternal menu = menus.get(item.getMenuId());
            return menu != null && menu.isOnSale();
        });
        return allOnSale ? Optional.empty() : Optional.of(OrderErrorCode.MENU_NOT_ON_SALE);
    }
}
