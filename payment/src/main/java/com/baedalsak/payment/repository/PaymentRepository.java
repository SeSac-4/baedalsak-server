package com.baedalsak.payment.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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

  // 고객의 진행 중 결제 요청 (#128). payment_customer_active_uq 로 고객당 최대 1건
  Optional<Payment> findByCustomerIdAndStatus(Long customerId, PaymentStatus status);

  // 조정자 점검용 일괄 조회 (#128). 결제가 없는 주문은 결과에 없다
  List<Payment> findByOrderIdIn(Collection<Long> orderIds);

  // 결제창만 열린(키 없음) 결제 요청을 만료한다 (#128). 그 사이 키가 저장됐으면 0행 — 키 저장과 동시에 와도 하나만 성공
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE Payment p
         SET p.status = :expired
       WHERE p.id = :id AND p.status = :requested AND p.tossPaymentKey IS NULL
      """)
  int expireIfKeyless(@Param("id") Long id, @Param("requested") PaymentStatus requested,
      @Param("expired") PaymentStatus expired);

  // 토스 조회로 미승인을 확인한 결제 요청을 만료한다 (#128). 저장된 키가 그대로일 때만
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE Payment p
         SET p.status = :expired
       WHERE p.id = :id AND p.status = :requested AND p.tossPaymentKey = :tossPaymentKey
      """)
  int expireWithKey(@Param("id") Long id, @Param("tossPaymentKey") String tossPaymentKey,
      @Param("requested") PaymentStatus requested, @Param("expired") PaymentStatus expired);

  // 내 결제 목록 (#127): 승인 완료 · 환불 처리 중 · 환불 완료처럼 실제로 결제된 상태들
  Page<Payment> findByCustomerIdAndStatusIn(Long customerId, Collection<PaymentStatus> statuses, Pageable pageable);

  // 상태 전이 한 칸 (#127): from 일 때만 to 로. 0행이면 다른 처리가 먼저 바꿈 (승인 완료 → 환불 처리 중 → 환불 완료)
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("UPDATE Payment p SET p.status = :to WHERE p.id = :id AND p.status = :from")
  int changeStatus(@Param("id") Long id, @Param("from") PaymentStatus from, @Param("to") PaymentStatus to);

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
