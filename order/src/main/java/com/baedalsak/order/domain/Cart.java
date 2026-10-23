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
    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private Cart(Long customerId, Long storeId) {
        this.customerId = customerId;
        this.storeId = storeId;
    }

    public static Cart create(Long customerId, Long storeId) {
        return new Cart(customerId, storeId);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
