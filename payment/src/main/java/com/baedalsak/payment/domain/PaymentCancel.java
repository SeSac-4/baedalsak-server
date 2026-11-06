package com.baedalsak.payment.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 취소(환불) 요청 1건 (#127, 설계서 '테이블 정의서' payment_cancel). 지금은 전액 취소만, 결제당 진행 중 취소 1건.
 * 상태 변경은 PaymentCancelRepository 의 조건부 UPDATE 로만 한다.
 */
@Entity
@Table(name = "payment_cancel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long paymentId;

  @Column(nullable = false, updatable = false)
  private Integer amount;

  @Column(nullable = false, updatable = false, length = 200)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private CancelRequester requestedBy;

  // 토스 취소 멱등 키. 재시도 · 재전달 · 다른 명령 모두 이 값을 그대로 쓴다 (에러 뒤 키를 바꾸지 않음)
  @Column(nullable = false, updatable = false, length = 64)
  private String idempotencyKey;

  @Column(length = 64)
  private String tossTransactionKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PaymentCancelStatus status;

  @Column(nullable = false)
  private Integer attemptCount;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime requestedAt;

  private OffsetDateTime canceledAt;

  // 전액 취소 요청을 새로 만든다 (진행 중, 멱등 키 새로 발급)
  public static PaymentCancel fullCancel(Payment payment, String reason, CancelRequester requestedBy) {
    PaymentCancel cancel = new PaymentCancel();
    cancel.paymentId = payment.getId();
    cancel.amount = payment.getAmount();
    cancel.reason = reason;
    cancel.requestedBy = requestedBy;
    cancel.idempotencyKey = UUID.randomUUID().toString();
    cancel.status = PaymentCancelStatus.PENDING;
    cancel.attemptCount = 0;
    cancel.requestedAt = OffsetDateTime.now();
    return cancel;
  }
}
