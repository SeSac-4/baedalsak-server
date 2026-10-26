package com.baedalsak.order.repository;

import com.baedalsak.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 내 주문 최신순 (orders_customer_idx: customer_id, created_at DESC)
    Page<Order> findAllByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);
}
