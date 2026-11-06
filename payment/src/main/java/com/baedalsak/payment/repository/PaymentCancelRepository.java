package com.baedalsak.payment.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

  // 결제의 진행 중 취소 (payment_cancel_pending_uq 로 결제당 최대 1건)
  Optional<PaymentCancel> findByPaymentIdAndStatus(Long paymentId, PaymentCancelStatus status);

  // 결제의 가장 최근 취소 요청 (결제 상세 환불 표시)
  Optional<PaymentCancel> findTopByPaymentIdOrderByIdDesc(Long paymentId);

  // 오래 머문 진행 중 취소 개수 (감시)
  long countByStatusAndRequestedAtBefore(PaymentCancelStatus status, OffsetDateTime before);

  // 토스 취소를 부를 때마다 1 증가 (재시도 횟수 추적)
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("UPDATE PaymentCancel c SET c.attemptCount = c.attemptCount + 1 WHERE c.id = :id")
  int increaseAttempt(@Param("id") Long id);

  // 진행 중일 때만 완료로 (0행이면 다른 처리가 먼저 끝냄)
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE PaymentCancel c
         SET c.status = :done, c.tossTransactionKey = :transactionKey, c.canceledAt = :canceledAt
       WHERE c.id = :id AND c.status = :pending
      """)
  int markDone(@Param("id") Long id, @Param("transactionKey") String transactionKey,
      @Param("canceledAt") OffsetDateTime canceledAt,
      @Param("pending") PaymentCancelStatus pending, @Param("done") PaymentCancelStatus done);

  // 진행 중일 때만 실패로 (토스 확정 거절)
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("UPDATE PaymentCancel c SET c.status = :failed WHERE c.id = :id AND c.status = :pending")
  int markFailed(@Param("id") Long id,
      @Param("pending") PaymentCancelStatus pending, @Param("failed") PaymentCancelStatus failed);
}
