package com.baedalsak.order.repository;

import com.baedalsak.order.domain.Cart;
import com.baedalsak.order.domain.CartItem;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 저장소. 서비스는 이 인터페이스만 쓰고, 저장 방식(지금은 DB 테이블)은 구현체가 정한다 (설계서 N3.2).
 */
public interface CartRepository {

    Optional<Cart> findCart(Long customerId);

    List<CartItem> findItems(Long cartId);

    Optional<CartItem> findItem(Long cartId, Long menuId);

    Cart saveCart(Cart cart);

    CartItem saveItem(CartItem item);

    void deleteItem(CartItem item);

    // 항목까지 함께 삭제 (cart_item ON DELETE CASCADE)
    void deleteCart(Cart cart);
}
