package com.baedalsak.payment.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  // 주문당 결제 1건 (order_id UNIQUE)
  Optional<Payment> findByOrderId(Long orderId);

  // 결제 승인 요청의 orderId = 토스 orderId (toss_order_id UNIQUE)
  Optional<Payment> findByTossOrderId(String tossOrderId);

  // 결제 요청됨일 때만 승인 완료로 바꾼다. 0행이면 이미 승인된 결제 (409 PAYMENT_ALREADY_APPROVED)
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE Payment p
         SET p.status = :approved, p.tossPaymentKey = :tossPaymentKey, p.method = :method,
             p.approvedAt = :approvedAt, p.receiptUrl = :receiptUrl
       WHERE p.id = :id AND p.status = :requested
      """)
  int approve(@Param("id") Long id, @Param("tossPaymentKey") String tossPaymentKey, @Param("method") String method,
      @Param("approvedAt") OffsetDateTime approvedAt, @Param("receiptUrl") String receiptUrl,
      @Param("requested") PaymentStatus requested, @Param("approved") PaymentStatus approved);
}
