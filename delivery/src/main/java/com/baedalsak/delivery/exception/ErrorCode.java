package com.baedalsak.delivery.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 (설계서 'API 명세' 탭 에러 코드 표). message 는 사용자에게 그대로 보여 줄 수 있는 한국어.
 * 프론트는 code 로 분기한다 (예: R-01 에서 DELIVERY_ALREADY_ASSIGNED → 토스트 + 목록 새로고침).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "배달 정보를 찾을 수 없습니다."),
    DELIVERY_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "이미 배차된 주문입니다."),
    DELIVERY_INVALID_STATUS(HttpStatus.CONFLICT, "현재 배달 상태에서는 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
