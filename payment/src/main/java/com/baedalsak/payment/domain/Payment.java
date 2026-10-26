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

  // 이하 5개: 결제 요청 생성 시 order 내부 API(/internal/orders) 응답 스냅샷 (PaymentApproved 페이로드·결제완료 메일 수신자)
  @Column(nullable = false, updatable = false)
  private Long ownerId;

  @Column(nullable = false, updatable = false)
  private Long storeId;

  @Column(nullable = false, updatable = false, length = 100)
  private String storeName;

  @Column(nullable = false, updatable = false)
  private String customerEmail;

  @Column(nullable = false, updatable = false)
  private String ownerEmail;

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
  public static Payment request(Long orderId, Long customerId, Long ownerId, Long storeId, String storeName,
      String customerEmail, String ownerEmail, String tossOrderId, int amount) {
    Payment payment = new Payment();
    payment.orderId = orderId;
    payment.customerId = customerId;
    payment.ownerId = ownerId;
    payment.storeId = storeId;
    payment.storeName = storeName;
    payment.customerEmail = customerEmail;
    payment.ownerEmail = ownerEmail;
    payment.tossOrderId = tossOrderId;
    payment.amount = amount;
    payment.status = PaymentStatus.REQUESTED;
    payment.createdAt = OffsetDateTime.now();
    return payment;
  }
}
