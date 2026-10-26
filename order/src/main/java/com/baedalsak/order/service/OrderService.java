package com.baedalsak.order.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.order.client.MemberClient;
import com.baedalsak.order.client.MemberInternal;
import com.baedalsak.order.client.MenuInternal;
import com.baedalsak.order.client.StoreClient;
import com.baedalsak.order.client.StoreInternal;
import com.baedalsak.order.domain.Cart;
import com.baedalsak.order.domain.CartItem;
import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderItem;
import com.baedalsak.order.dto.request.CreateOrderRequest;
import com.baedalsak.order.dto.response.OrderDetailResponse;
import com.baedalsak.order.error.OrderErrorCode;
import com.baedalsak.order.repository.CartRepository;
import com.baedalsak.order.repository.OrderItemRepository;
import com.baedalsak.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문 생성 (4.1.1). 장바구니 기준으로 store·member 내부 API 로 다시 검증하고, 금액은 서버가 계산한다 (N4.7).
 * 외부 호출은 트랜잭션 밖에서 먼저 끝내고, 저장(주문·항목·장바구니 비우기)만 한 트랜잭션으로 묶는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StoreClient storeClient;
    private final MemberClient memberClient;
    private final TransactionTemplate transactionTemplate;

    public OrderDetailResponse create(LoginMember member, CreateOrderRequest request) {
        Cart cart = cartRepository.findCart(member.id())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_EMPTY));
        List<CartItem> cartItems = cartRepository.findItems(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessException(OrderErrorCode.CART_EMPTY);
        }

        // 1) 가게: 영업 여부·배달비·최소주문금액·사장님은 지금 값으로 다시 확인
        StoreInternal store = storeClient.getStore(cart.getStoreId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.STORE_NOT_FOUND));
        if (!store.isOpenNow()) {
            throw new BusinessException(OrderErrorCode.STORE_CLOSED);
        }

        // 2) 메뉴: 장바구니 스냅샷이 아니라 지금 가격·판매 상태로 계산 (없는·삭제된·품절 메뉴는 MENU_NOT_ON_SALE)
        Map<Long, MenuInternal> menus = storeClient.getMenus(cart.getStoreId(),
                        cartItems.stream().map(CartItem::getMenuId).toList())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.STORE_NOT_FOUND))
                .stream()
                .collect(Collectors.toMap(MenuInternal::id, Function.identity()));
        int itemsAmount = 0;
        for (CartItem item : cartItems) {
            MenuInternal menu = menus.get(item.getMenuId());
            if (menu == null || !menu.isOnSale()) {
                throw new BusinessException(OrderErrorCode.MENU_NOT_ON_SALE);
            }
            itemsAmount += menu.price() * item.getQuantity();
        }
        if (itemsAmount < store.minOrderAmount()) {
            throw new BusinessException(OrderErrorCode.MIN_ORDER_AMOUNT_NOT_MET);
        }

        // 3) 회원: 고객 phone·email, 사장님 email 스냅샷 (설계서 '동기 호출' 탭: ownerId 로 한 번 더 호출)
        MemberInternal customer = memberClient.getMember(member.id())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        MemberInternal owner = memberClient.getMember(store.ownerId())
                .orElseThrow(() -> {
                    log.error("가게 사장님 회원 없음 storeId={}, ownerId={}", cart.getStoreId(), store.ownerId());
                    return new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
                });

        Order order = Order.builder()
                .customerId(member.id())
                .storeId(cart.getStoreId())
                .ownerId(store.ownerId())
                .storeName(store.name())
                .deliveryAddress(request.deliveryAddress())
                .deliveryDong(request.deliveryDong())
                .pickupAddress(store.address())
                .customerPhone(customer.phone())
                .customerEmail(customer.email())
                .ownerEmail(owner.email())
                .storeRequest(request.storeRequest())
                .riderRequest(request.riderRequest())
                .itemsAmount(itemsAmount)
                .deliveryFee(store.deliveryFee())
                .build();

        return transactionTemplate.execute(status -> {
            Order saved = orderRepository.save(order);
            List<OrderItem> orderItems = orderItemRepository.saveAll(cartItems.stream()
                    .map(item -> {
                        MenuInternal menu = menus.get(item.getMenuId());
                        return OrderItem.create(saved.getId(), menu.id(), menu.name(), menu.price(),
                                item.getQuantity());
                    })
                    .toList());
            // 주문을 만들면 장바구니는 비운다 (3.1.1)
            cartRepository.deleteCart(cart);
            return OrderDetailResponse.of(saved, orderItems);
        });
    }
}
