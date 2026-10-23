package com.baedalsak.order.error;

import com.baedalsak.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * order 도메인 에러 코드 (설계서 'API 명세' 에러표). 공통 코드는 CommonErrorCode 를 쓴다.
 * STORE_NOT_FOUND·STORE_CLOSED·MENU_NOT_ON_SALE 은 store 내부 API 검증 결과를 order 가 응답할 때 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    STORE_NOT_FOUND(404, "가게를 찾을 수 없습니다."),
    STORE_CLOSED(400, "영업 중인 가게가 아닙니다."),
    MENU_NOT_ON_SALE(400, "판매 중이 아닌 메뉴가 있습니다."),
    CART_DIFFERENT_STORE(409, "장바구니에 다른 가게의 메뉴가 있습니다."),
    CART_EMPTY(400, "장바구니가 비어 있습니다."),
    MIN_ORDER_AMOUNT_NOT_MET(400, "최소 주문 금액을 충족하지 못했습니다.");

    private final int status;
    private final String message;
}
