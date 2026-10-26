package com.baedalsak.order.repository;

import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 내 주문 최신순 (orders_customer_idx: customer_id, created_at DESC)
    Page<Order> findAllByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);

    // 가게 주문 목록 (orders_store_status_idx: store_id, status, created_at DESC)
    Page<Order> findAllByStoreIdAndOwnerIdAndStatusInOrderByCreatedAtDescIdDesc(
            Long storeId, Long ownerId, Collection<OrderStatus> statuses, Pageable pageable);

    // 이 가게 주문이 다른 사장님 것이면 내 가게가 아니다 (조회 시 store 를 부르지 않고 owner_id 스냅샷으로 판단)
    boolean existsByStoreIdAndOwnerIdNot(Long storeId, Long ownerId);

}
