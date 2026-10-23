package com.baedalsak.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목 (order_item 테이블). 주문 시점의 메뉴명·단가 스냅샷.
 */
@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 객체 연관관계 대신 ID 만 보관 (docs 4-2)
    @Column(nullable = false, updatable = false)
    private Long orderId;

    // store.menu.id
    @Column(nullable = false, updatable = false)
    private Long menuId;

    @Column(nullable = false, length = 100, updatable = false)
    private String menuName;

    // 주문 시점 단가
    @Column(nullable = false, updatable = false)
    private Integer price;

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    private OrderItem(Long orderId, Long menuId, String menuName, Integer price, Integer quantity) {
        this.orderId = orderId;
        this.menuId = menuId;
        this.menuName = menuName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderItem create(Long orderId, Long menuId, String menuName, Integer price, Integer quantity) {
        return new OrderItem(orderId, menuId, menuName, price, quantity);
    }
}
