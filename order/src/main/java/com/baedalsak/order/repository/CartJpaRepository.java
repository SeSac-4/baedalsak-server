package com.baedalsak.order.repository;

import com.baedalsak.order.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CartJpaRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByCustomerId(Long customerId);
}
