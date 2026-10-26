package com.baedalsak.order.controller;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.order.dto.request.AddCartItemRequest;
import com.baedalsak.order.dto.request.UpdateCartItemRequest;
import com.baedalsak.order.dto.response.CartResponse;
import com.baedalsak.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니 API (고객). gateway 가 /v1/cart 이하를 이 서비스로 보낸다.
 */
@RestController
@RequestMapping("/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // 3.1.2 장바구니 조회
    @GetMapping
    public CartResponse getCart(@CurrentMember LoginMember member) {
        return cartService.getCart(member);
    }

    // 3.1.1 담기. 다른 가게 메뉴가 있으면 409 CART_DIFFERENT_STORE → replace=true 로 재요청하면 비우고 담음
    @PostMapping("/items")
    public CartResponse addItem(@CurrentMember LoginMember member,
                                @Valid @RequestBody AddCartItemRequest request,
                                @RequestParam(defaultValue = "false") boolean replace) {
        return cartService.addItem(member, request, replace);
    }

    // 3.1.1 수량 변경
    @PatchMapping("/items/{menuId}")
    public CartResponse changeQuantity(@CurrentMember LoginMember member, @PathVariable Long menuId,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.changeQuantity(member, menuId, request);
    }

    // 3.1.1 항목 삭제
    @DeleteMapping("/items/{menuId}")
    public CartResponse removeItem(@CurrentMember LoginMember member, @PathVariable Long menuId) {
        return cartService.removeItem(member, menuId);
    }

    // 3.1.1 비우기
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@CurrentMember LoginMember member) {
        cartService.clear(member);
    }
}
