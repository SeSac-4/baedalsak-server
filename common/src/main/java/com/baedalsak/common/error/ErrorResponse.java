package com.baedalsak.common.error;

import java.util.List;


public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> errors,   // 검증 실패일 때만 값이 있고, 아니면 null
        String traceId
) {

    public record FieldErrorDetail(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null, traceId);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> errors, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), errors, traceId);
    }
}