package com.baedalsak.delivery.domain;

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
 * 배달 요청 (delivery 테이블). OrderAccepted 이벤트로 WAITING 상태로 생성된다 (6.1.1).
 * 가게·주소·고객 정보는 이벤트 페이로드 스냅샷이라 조회 시 다른 서비스를 호출하지 않는다.
 */
@Entity
@Table(name = "delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String storeName;

    @Column(nullable = false)
    private String pickupAddress;

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(nullable = false, length = 50)
    private String deliveryDong;

    @Column(nullable = false, length = 20)
    private String customerPhone;

    // 메일 수신자 (이벤트 customerEmail·ownerEmail). V2 이전에 생성된 행은 NULL
    private String customerEmail;

    private String ownerEmail;

    @Column(length = 200)
    private String riderRequest;

    @Column(nullable = false)
    private Integer deliveryFee;

    @Column(nullable = false)
    private OffsetDateTime estimatedReadyAt;

    private Long riderId;

    @Column(length = 50)
    private String riderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime assignedAt;

    private OffsetDateTime pickedUpAt;

    private OffsetDateTime completedAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private Delivery(Long orderId, Long customerId, Long ownerId, Long storeId, String storeName,
                     String pickupAddress, String deliveryAddress, String deliveryDong,
                     String customerPhone, String customerEmail, String ownerEmail,
                     String riderRequest, Integer deliveryFee, OffsetDateTime estimatedReadyAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.ownerId = ownerId;
        this.storeId = storeId;
        this.storeName = storeName;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.deliveryDong = deliveryDong;
        this.customerPhone = customerPhone;
        this.customerEmail = customerEmail;
        this.ownerEmail = ownerEmail;
        this.riderRequest = riderRequest;
        this.deliveryFee = deliveryFee;
        this.estimatedReadyAt = estimatedReadyAt;
        this.status = DeliveryStatus.WAITING;   // 생성된 요청은 배차 대기 (6.1.1)
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
