package com.baedalsak.payment.domain;

import java.time.OffsetDateTime;

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

@Entity 
@Table (name = "payment")
@Getter 
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

  @Id 
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long orderId;

  @Column(nullable = false, updatable = false)
  private Long customerId;

  @Column(nullable = false, updatable = false, length = 64)
  private String tossOrderId;

  @Column(nullable = false, updatable = false)
  private Integer amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(length = 200)
  private String tossPaymentKey;

  @Column (length = 30)
  private String method;

  private OffsetDateTime approvedAt;

  @Column(length = 500)
  private String receiptUrl;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;
  
  // 결제를 새로 만드는 방법
  public static Payment request(Long orderId, Long customerId, String tossOrderId, int amount) {
    Payment payment = new Payment();
    payment.orderId = orderId;
    payment.customerId = customerId;
    payment.tossOrderId = tossOrderId;
    payment.amount = amount;
    payment.status = PaymentStatus.REQUESTED;
    payment.createdAt = OffsetDateTime.now();
    return payment;
  }
}
