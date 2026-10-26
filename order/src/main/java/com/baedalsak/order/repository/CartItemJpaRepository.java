package com.baedalsak.order.repository;

import com.baedalsak.order.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findAllByCartIdOrderById(Long cartId);

    Optional<CartItem> findByCartIdAndMenuId(Long cartId, Long menuId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CartItem i WHERE i.cartId = :cartId")
    void deleteAllByCartId(Long cartId);
}
