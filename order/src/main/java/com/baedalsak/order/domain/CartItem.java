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
 * 장바구니 항목 (cart_item 테이블). 메뉴명·가격은 담을 때 스냅샷이라 조회 시 store 를 호출하지 않는다.
 */
@Entity
@Table(name = "cart_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 객체 연관관계 대신 ID 만 보관 (docs 4-2)
    @Column(nullable = false, updatable = false)
    private Long cartId;

    // store.menu.id
    @Column(nullable = false, updatable = false)
    private Long menuId;

    @Column(nullable = false, length = 100)
    private String menuName;

    // 주문 생성 시 store 에 재검증
    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer quantity;

    private CartItem(Long cartId, Long menuId, String menuName, Integer price, Integer quantity) {
        this.cartId = cartId;
        this.menuId = menuId;
        this.menuName = menuName;
        this.price = price;
        this.quantity = quantity;
    }

    public static CartItem create(Long cartId, Long menuId, String menuName, Integer price, Integer quantity) {
        return new CartItem(cartId, menuId, menuName, price, quantity);
    }

    // 같은 메뉴를 다시 담으면 수량을 더하고, 메뉴명·가격은 최신 값으로 갱신한다 (3.1.1)
    public void addQuantity(int quantity, String menuName, Integer price) {
        this.quantity += quantity;
        this.menuName = menuName;
        this.price = price;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int amount() {
        return price * quantity;
    }
}
