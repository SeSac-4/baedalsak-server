package com.baedalsak.store.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 에러 응답 본문 {code, message, errors?[{field, reason}], traceId} (설계서 'API 명세' 탭 공통 규칙)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, List<FieldError> errors, String traceId) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of(), traceId);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), errors, traceId);
    }
}
