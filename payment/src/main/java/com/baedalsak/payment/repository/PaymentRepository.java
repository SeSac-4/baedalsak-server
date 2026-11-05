package com.baedalsak.payment.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  // 내 결제 목록: 고객의 결제 중 한 상태만 (#93 승인 완료만). 정렬은 pageable 로 받는다
  Page<Payment> findByCustomerIdAndStatus(Long customerId, PaymentStatus status, Pageable pageable);

  // 토스 승인 호출 전에 paymentKey 를 저장한다 (#125). 비어 있거나 같은 키일 때만, 0행이면 다른 키가 있거나 결제 요청됨이 아님
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE Payment p
         SET p.tossPaymentKey = :tossPaymentKey
       WHERE p.id = :id AND p.status = :requested
         AND (p.tossPaymentKey IS NULL OR p.tossPaymentKey = :tossPaymentKey)
      """)
  int reservePaymentKey(@Param("id") Long id, @Param("tossPaymentKey") String tossPaymentKey,
      @Param("requested") PaymentStatus requested);

  // 토스가 승인하지 않았다고 확인해 준 키만 비운다 (#125). 저장된 키가 같을 때만
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE Payment p
         SET p.tossPaymentKey = NULL
       WHERE p.id = :id AND p.status = :requested AND p.tossPaymentKey = :tossPaymentKey
      """)
  int clearPaymentKey(@Param("id") Long id, @Param("tossPaymentKey") String tossPaymentKey,
      @Param("requested") PaymentStatus requested);

  // 결제 요청됨이고 저장해 둔 paymentKey 와 같을 때만 승인 완료로 바꾼다 (#125). 0행이면 409 PAYMENT_ALREADY_APPROVED
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE Payment p
         SET p.status = :approved, p.method = :method,
             p.approvedAt = :approvedAt, p.receiptUrl = :receiptUrl, p.approvalNumber = :approvalNumber
       WHERE p.id = :id AND p.status = :requested AND p.tossPaymentKey = :tossPaymentKey
      """)
  int approve(@Param("id") Long id, @Param("tossPaymentKey") String tossPaymentKey, @Param("method") String method,
      @Param("approvedAt") OffsetDateTime approvedAt, @Param("receiptUrl") String receiptUrl,
      @Param("approvalNumber") String approvalNumber,
      @Param("requested") PaymentStatus requested, @Param("approved") PaymentStatus approved);
}
