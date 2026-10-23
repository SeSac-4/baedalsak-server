package com.baedalsak.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 팀 에러 코드 (설계서 'API 명세' 에러표와 1:1).
 * 404는 세부 코드가 있는 리소스면 세부 코드를, 그 외(회원·장바구니 항목 등)는 NOT_FOUND를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(400, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    NOT_FOUND(404, "대상을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청입니다."),
    TOO_MANY_REQUESTS(429, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다."),
    DEPENDENCY_UNAVAILABLE(503, "일시적으로 서비스를 이용할 수 없습니다."),

    // 404 세부 코드
    STORE_NOT_FOUND(404, "가게를 찾을 수 없습니다."),
    MENU_NOT_FOUND(404, "메뉴를 찾을 수 없습니다."),
    ORDER_NOT_FOUND(404, "주문을 찾을 수 없습니다."),
    DELIVERY_NOT_FOUND(404, "배달을 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(404, "결제를 찾을 수 없습니다."),

    // member
    EMAIL_DUPLICATED(409, "이미 가입된 이메일입니다."),

    // store
    STORE_ALREADY_EXISTS(409, "이미 등록한 가게가 있습니다. 사장님은 가게 1개만 운영할 수 있습니다."),
    STORE_CLOSED(400, "영업 중인 가게가 아닙니다."),
    MENU_NOT_ON_SALE(400, "판매 중이 아닌 메뉴가 있습니다."),

    // order (장바구니·주문)
    CART_DIFFERENT_STORE(409, "장바구니에 다른 가게의 메뉴가 있습니다."),
    CART_EMPTY(400, "장바구니가 비어 있습니다."),
    MIN_ORDER_AMOUNT_NOT_MET(400, "최소 주문 금액을 충족하지 못했습니다."),
    ORDER_INVALID_STATUS(409, "현재 주문 상태에서는 처리할 수 없습니다."),

    // payment
    PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 일치하지 않습니다."),
    PAYMENT_ALREADY_APPROVED(409, "이미 승인된 결제입니다."),
    PAYMENT_GATEWAY_ERROR(502, "결제 승인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // delivery
    DELIVERY_ALREADY_ASSIGNED(409, "이미 배차된 주문입니다."),
    DELIVERY_INVALID_STATUS(409, "현재 배달 상태에서는 처리할 수 없습니다.");

    private final int status;
    private final String message;
}