package com.baedalsak.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 장바구니 (cart 테이블). 고객당 1개, 한 가게의 메뉴만 담는다 (3.1.1).
 * 가게명·배달비·최소주문금액은 담을 때 store 에서 받은 스냅샷이라 조회 시 store 를 호출하지 않는다.
 */
@Entity
@Table(name = "cart")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // member.id (다른 서비스라 FK 없음)
    @Column(nullable = false, unique = true, updatable = false)
    private Long customerId;

    // 담긴 메뉴의 가게 (store.id)
    @Column(nullable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String storeName;

    @Column(nullable = false)
    private Integer deliveryFee;

    @Column(nullable = false)
    private Integer minOrderAmount;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private Cart(Long customerId, Long storeId, String storeName, Integer deliveryFee, Integer minOrderAmount) {
        this.customerId = customerId;
        this.storeId = storeId;
        refreshStore(storeName, deliveryFee, minOrderAmount);
    }

    public static Cart create(Long customerId, Long storeId, String storeName,
                              Integer deliveryFee, Integer minOrderAmount) {
        return new Cart(customerId, storeId, storeName, deliveryFee, minOrderAmount);
    }

    public boolean isOfStore(Long storeId) {
        return this.storeId.equals(storeId);
    }

    // 담을 때마다 store 의 최신 값으로 갱신
    public void refreshStore(String storeName, Integer deliveryFee, Integer minOrderAmount) {
        this.storeName = storeName;
        this.deliveryFee = deliveryFee;
        this.minOrderAmount = minOrderAmount;
        this.updatedAt = OffsetDateTime.now();
    }

    // 항목만 바뀐 경우에도 수정 시각을 남긴다
    public void markUpdated() {
        this.updatedAt = OffsetDateTime.now();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
