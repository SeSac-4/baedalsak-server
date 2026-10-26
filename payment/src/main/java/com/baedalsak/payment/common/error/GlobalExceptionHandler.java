package com.baedalsak.payment.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 모든 에러를 {code, message, errors[], traceId} 형식으로 응답한다.
 * traceId 는 Micrometer Tracing 이 MDC 에 넣은 값을 쓴다 (docs 6-2).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return respond(e.getErrorCode(), List.of());
    }

    // @Valid 본문 검증 실패 → 필드별 사유
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, errors);
    }

    // @Validated 파라미터 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<ErrorResponse.FieldError> errors = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, errors);
    }

    // 본문 JSON 형식 오류, 쿼리·경로 파라미터 타입 오류(예: /payments/abc), 필수 파라미터 누락
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return respond(ErrorCode.VALIDATION_FAILED, List.of());
    }

    // 없는 경로 → 404
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND, List.of());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, List<ErrorResponse.FieldError> errors) {
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, errors, MDC.get("traceId")));
    }
}
