package com.baedalsak.order.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.order.client.MenuInternal;
import com.baedalsak.order.client.StoreClient;
import com.baedalsak.order.client.StoreInternal;
import com.baedalsak.order.domain.Cart;
import com.baedalsak.order.domain.CartItem;
import com.baedalsak.order.dto.request.AddCartItemRequest;
import com.baedalsak.order.dto.request.UpdateCartItemRequest;
import com.baedalsak.order.dto.response.CartResponse;
import com.baedalsak.order.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 (3.1.1 · 3.1.2). 담을 때만 store 내부 API 로 영업·판매 상태를 검증하고, 조회는 스냅샷으로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final StoreClient storeClient;

    @Transactional(readOnly = true)
    public CartResponse getCart(LoginMember member) {
        return cartRepository.findCart(member.id())
                .map(cart -> CartResponse.of(cart, cartRepository.findItems(cart.getId())))
                .orElseGet(CartResponse::empty);
    }

    // 다른 가게 메뉴가 담겨 있으면 409 CART_DIFFERENT_STORE. replace=true 면 비우고 담는다
    @Transactional
    public CartResponse addItem(LoginMember member, AddCartItemRequest request, boolean replace) {
        StoreInternal store = storeClient.getStore(request.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (!store.isOpenNow()) {
            throw new BusinessException(ErrorCode.STORE_CLOSED);
        }
        MenuInternal menu = findOnSaleMenu(request.storeId(), request.menuId());

        Cart cart = prepareCart(member.id(), request.storeId(), store, replace);
        Optional<CartItem> existing = cartRepository.findItem(cart.getId(), menu.id());
        if (existing.isPresent()) {
            existing.get().addQuantity(request.quantity(), menu.name(), menu.price());
        } else {
            cartRepository.saveItem(CartItem.create(cart.getId(), menu.id(), menu.name(), menu.price(),
                    request.quantity()));
        }
        return CartResponse.of(cart, cartRepository.findItems(cart.getId()));
    }

    @Transactional
    public CartResponse changeQuantity(LoginMember member, Long menuId, UpdateCartItemRequest request) {
        Cart cart = getExistingCart(member.id());
        CartItem item = getExistingItem(cart, menuId);
        item.changeQuantity(request.quantity());
        cart.markUpdated();
        return CartResponse.of(cart, cartRepository.findItems(cart.getId()));
    }

    // 마지막 항목을 지우면 장바구니도 지운다
    @Transactional
    public CartResponse removeItem(LoginMember member, Long menuId) {
        Cart cart = getExistingCart(member.id());
        cartRepository.deleteItem(getExistingItem(cart, menuId));
        List<CartItem> items = cartRepository.findItems(cart.getId());
        if (items.isEmpty()) {
            cartRepository.deleteCart(cart);
            return CartResponse.empty();
        }
        cart.markUpdated();
        return CartResponse.of(cart, items);
    }

    @Transactional
    public void clear(LoginMember member) {
        cartRepository.findCart(member.id()).ifPresent(cartRepository::deleteCart);
    }

    // 없는 메뉴·삭제된 메뉴(응답에서 빠짐)·품절 메뉴는 MENU_NOT_ON_SALE (API 명세 K27)
    private MenuInternal findOnSaleMenu(Long storeId, Long menuId) {
        List<MenuInternal> menus = storeClient.getMenus(storeId, List.of(menuId))
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        return menus.stream()
                .filter(menu -> menu.id().equals(menuId) && menu.isOnSale())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_ON_SALE));
    }

    // 같은 가게면 가게 스냅샷을 최신 값으로 갱신, 다른 가게면 replace 일 때만 비우고 새로 만든다
    private Cart prepareCart(Long customerId, Long storeId, StoreInternal store, boolean replace) {
        Optional<Cart> found = cartRepository.findCart(customerId);
        if (found.isPresent() && found.get().isOfStore(storeId)) {
            Cart cart = found.get();
            cart.refreshStore(store.name(), store.deliveryFee(), store.minOrderAmount());
            return cart;
        }
        if (found.isPresent()) {
            if (!replace) {
                throw new BusinessException(ErrorCode.CART_DIFFERENT_STORE);
            }
            cartRepository.deleteCart(found.get());
        }
        return cartRepository.saveCart(Cart.create(customerId, storeId, store.name(),
                store.deliveryFee(), store.minOrderAmount()));
    }

    // 장바구니·항목이 없으면 404 NOT_FOUND (세부 코드가 없는 리소스, API 명세 65행)
    private Cart getExistingCart(Long customerId) {
        return cartRepository.findCart(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private CartItem getExistingItem(Cart cart, Long menuId) {
        return cartRepository.findItem(cart.getId(), menuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
