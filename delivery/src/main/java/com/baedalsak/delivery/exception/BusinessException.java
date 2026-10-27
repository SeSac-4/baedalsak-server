package com.baedalsak.delivery.exception;

import lombok.Getter;

/**
 * 서비스 로직에서 던지는 예외. GlobalExceptionHandler 가 ErrorCode 의 상태·메시지로 응답한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    // 같은 코드라도 상황에 맞는 메시지가 필요할 때 (예: FORBIDDEN 을 픽업·배달 상세에서 다른 문구로). code 는 그대로라 프론트 분기에 영향 없음
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
