package com.baedalsak.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  // 주문당 결제 1건 (order_id UNIQUE)
  Optional<Payment> findByOrderId(Long orderId);
}
