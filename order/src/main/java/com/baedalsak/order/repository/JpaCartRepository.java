package com.baedalsak.order.repository;

import com.baedalsak.order.domain.Cart;
import com.baedalsak.order.domain.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CartRepository 의 DB 테이블(cart·cart_item) 구현
 */
@Repository
@RequiredArgsConstructor
public class JpaCartRepository implements CartRepository {

    private final CartJpaRepository carts;
    private final CartItemJpaRepository items;

    @Override
    public Optional<Cart> findCart(Long customerId) {
        return carts.findByCustomerId(customerId);
    }

    @Override
    public List<CartItem> findItems(Long cartId) {
        return items.findAllByCartIdOrderById(cartId);
    }

    @Override
    public Optional<CartItem> findItem(Long cartId, Long menuId) {
        return items.findByCartIdAndMenuId(cartId, menuId);
    }

    @Override
    public Cart saveCart(Cart cart) {
        return carts.saveAndFlush(cart);
    }

    @Override
    public CartItem saveItem(CartItem item) {
        return items.save(item);
    }

    @Override
    public void deleteItem(CartItem item) {
        items.delete(item);
    }

    @Override
    public void deleteCart(Cart cart) {
        items.deleteAllByCartId(cart.getId());
        carts.delete(cart);
        carts.flush();
    }
}
