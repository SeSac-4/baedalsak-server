package com.baedalsak.payment.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 (설계서 'API 명세' 탭 에러 코드 표). message 는 사용자에게 그대로 보여 줄 수 있는 한국어.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다."),
    ORDER_INVALID_STATUS(HttpStatus.CONFLICT, "결제할 수 없는 주문 상태입니다."),
    PAYMENT_ALREADY_APPROVED(HttpStatus.CONFLICT, "이미 승인된 결제입니다."),
    // 문구는 설계서 '화면 흐름' 탭 U-06 비고
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "결제 확인 중 오류. 다시 시도해 주세요");

    private final HttpStatus status;
    private final String message;
}
