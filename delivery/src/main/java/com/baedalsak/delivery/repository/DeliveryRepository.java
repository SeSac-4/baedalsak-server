package com.baedalsak.delivery.repository;

import com.baedalsak.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * delivery 테이블.
 */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByOrderId(Long orderId);
}
