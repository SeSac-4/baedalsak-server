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

import java.util.List;

/**
 * 주문 항목 (order_item 테이블). 주문 시점의 메뉴명·단가 스냅샷.
 */
@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    // 토스 orderName 최대 길이
    private static final int ORDER_NAME_MAX_LENGTH = 100;

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

    // 토스 결제창 주문명: 첫 번째 항목 메뉴명 + " 외 N건" (N = 항목 수 - 1, 수량은 세지 않음). 100자를 넘으면 메뉴명을 줄인다
    public static String orderName(List<OrderItem> items) {
        String first = items.getFirst().getMenuName();
        if (items.size() == 1) {
            return first;
        }
        String suffix = " 외 " + (items.size() - 1) + "건";
        int maxFirstLength = ORDER_NAME_MAX_LENGTH - suffix.length();
        return (first.length() > maxFirstLength ? first.substring(0, maxFirstLength) : first) + suffix;
    }
}
