package com.baedalsak.payment.common.error;

import lombok.Getter;

/**
 * 서비스 로직에서 던지는 예외. GlobalExceptionHandler 가 ErrorCode 의 상태·메시지로 응답한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
