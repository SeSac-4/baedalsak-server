package com.baedalsak.store.domain;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 가게 (store 테이블). 사장님 1명 = 가게 1개 (owner_id UNIQUE).
 * 등록 직후 운영 상태는 임시 중지(PAUSED)로 시작한다 (1.2.1).
 */
@Entity
@Table(name = "store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // member.id (다른 서비스라 FK 없음)
    @Column(nullable = false, unique = true, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private Integer minOrderAmount;

    @Column(nullable = false)
    private Integer deliveryFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OperationStatus operationStatus;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private Store(Long ownerId, String name, Category category, String address,
                  Integer minOrderAmount, Integer deliveryFee) {
        this.ownerId = ownerId;
        this.name = name;
        this.category = category;
        this.address = address;
        this.minOrderAmount = minOrderAmount;
        this.deliveryFee = deliveryFee;
        this.operationStatus = OperationStatus.PAUSED;
    }

    public static Store create(Long ownerId, String name, Category category, String address,
                               Integer minOrderAmount, Integer deliveryFee) {
        return new Store(ownerId, name, category, address, minOrderAmount, deliveryFee);
    }

    public void update(String name, Category category, String address, Integer minOrderAmount, Integer deliveryFee) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.minOrderAmount = minOrderAmount;
        this.deliveryFee = deliveryFee;
    }

    public void changeOperationStatus(OperationStatus operationStatus) {
        this.operationStatus = operationStatus;
    }

    public boolean isOwnedBy(Long memberId) {
        return ownerId.equals(memberId);
    }

    // 영업 여부 = 정상 운영 AND 현재 시각이 오늘 영업시간 안 (1.4.2)
    public boolean isOpenAt(BusinessHour todayHour, LocalTime now) {
        return operationStatus == OperationStatus.OPEN && todayHour.isOpenAt(now);
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
