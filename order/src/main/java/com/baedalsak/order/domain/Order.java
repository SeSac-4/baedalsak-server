package com.baedalsak.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 주문 (orders 테이블, order 는 예약어). 가게·고객 정보와 메일 수신자 이메일은 생성 시 스냅샷이라
 * 이후 조회·이벤트 발행 때 다른 서비스를 호출하지 않는다. 결제대기(PENDING_PAYMENT)로 생성된다 (4.1.1).
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 다른 서비스 ID 는 값만 보관 (FK 없음)
    @Column(nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, updatable = false)
    private Long storeId;

    // 수락 권한 검증, 사장님 이메일 조회 키
    @Column(nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100, updatable = false)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private String deliveryAddress;

    // OrderAccepted 페이로드로 delivery 에 전달. 로그에는 마스킹 (N4.4)
    @Column(nullable = false, length = 20, updatable = false)
    private String customerPhone;

    // 메일 수신자 (이벤트 customerEmail·ownerEmail)
    @Column(nullable = false, updatable = false)
    private String customerEmail;

    @Column(nullable = false, updatable = false)
    private String ownerEmail;

    @Column(length = 200, updatable = false)
    private String storeRequest;

    @Column(length = 200, updatable = false)
    private String riderRequest;

    // Σ(price × quantity), 서버 계산 (N4.7)
    @Column(nullable = false, updatable = false)
    private Integer itemsAmount;

    @Column(nullable = false, updatable = false)
    private Integer deliveryFee;

    // items_amount + delivery_fee (DB CHECK 로도 강제)
    @Column(nullable = false, updatable = false)
    private Integer totalAmount;

    // 수락 시 now() + 조리 예상 분
    private OffsetDateTime estimatedReadyAt;

    // DeliveryAssigned 수신 시 저장
    @Column(length = 50)
    private String riderName;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // 총액은 받지 않고 항목 금액 + 배달비로 계산한다
    @Builder
    private Order(Long customerId, Long storeId, Long ownerId, String storeName, String deliveryAddress,
                  String customerPhone, String customerEmail, String ownerEmail,
                  String storeRequest, String riderRequest, Integer itemsAmount, Integer deliveryFee) {
        this.customerId = customerId;
        this.storeId = storeId;
        this.ownerId = ownerId;
        this.storeName = storeName;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.deliveryAddress = deliveryAddress;
        this.customerPhone = customerPhone;
        this.customerEmail = customerEmail;
        this.ownerEmail = ownerEmail;
        this.storeRequest = storeRequest;
        this.riderRequest = riderRequest;
        this.itemsAmount = itemsAmount;
        this.deliveryFee = deliveryFee;
        this.totalAmount = itemsAmount + deliveryFee;
    }

    public boolean isOrderedBy(Long memberId) {
        return customerId.equals(memberId);
    }

    public boolean isOwnedBy(Long memberId) {
        return ownerId.equals(memberId);
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
