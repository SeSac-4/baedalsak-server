package com.baedalsak.delivery.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 에러 응답 본문 {code, message, traceId} (설계서 'API 명세' 탭 공통 규칙).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String traceId) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        return new ErrorResponse(errorCode.name(), message, traceId);
    }
}
