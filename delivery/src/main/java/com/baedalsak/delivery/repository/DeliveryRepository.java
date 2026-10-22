package com.baedalsak.delivery.repository;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * delivery 테이블.
 */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByOrderId(Long orderId);

    // 배차 대기 목록 (6.2.1, 오래된 순). delivery_waiting_idx (created_at) WHERE status='WAITING' 사용
    List<Delivery> findAllByStatusOrderByCreatedAtAscIdAsc(DeliveryStatus status);
}
