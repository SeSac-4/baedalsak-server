package com.baedalsak.order.repository;

import com.baedalsak.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrderIdOrderById(Long orderId);

    // 주문 목록 한 페이지의 항목을 한 번에 조회
    List<OrderItem> findAllByOrderIdInOrderById(Collection<Long> orderIds);
}
