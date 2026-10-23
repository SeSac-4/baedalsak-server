package com.baedalsak.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(400, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    NOT_FOUND(404, "대상을 찾을 수 없습니다."),
    EMAIL_DUPLICATED(409, "이미 가입된 이메일입니다."),
    TOO_MANY_REQUESTS(429, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다."),
    DEPENDENCY_UNAVAILABLE(503, "일시적으로 서비스를 이용할 수 없습니다.");
    /*
    허용되지 않는 HTTP 메서드(예: GET 경로에 POST)는 지금 500으로 떨어짐. 팀 에러 코드에 405가 없어서 일단 500

    
    [예상되는 각 서비스 에러코드]
    STORE_ALREADY_EXISTS(409, "이미 존재하는 매장입니다."),
    CART_DIFFERENT_STORE(409, "장바구니에 다른 매장의 상품이 있습니다."),STORE_CLOSED(400, "영업 중인 매장이 아닙니다."),
    MENU_NOT_ON_SALE(400, "판매 중인 메뉴가 아닙니다."),
    CART_EMPTY(400, "장바구니가 비어 있습니다."),
    MIN_ORDER_AMOUNT_NOT_MET(400, "최소 주문 금액을 충족하지 못했습니다."),
    ORDER_INVALID_STATUS(409, "유효하지 않은 주문 상태입니다."),PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 일치하지 않습니다."),PAYMENT_ALREADY_APPROVED(409, "이미 승인된 결제입니다."),PAYMENT_GATEWAY_ERROR(502, "결제 게이트웨이 오류가 발생했습니다."),DELIVERY_ALREADY_ASSIGNED(409, "이미 배달이 배정되었습니다."),
    DELIVERY_INVALID_STATUS(409, "유효하지 않은 배달 상태입니다.");
     */

    private final int status;
    private final String message;
}