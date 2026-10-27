package com.baedalsak.delivery.exception;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * HTTP 에러 응답. @Valid 실패(400)는 Spring 기본 처리를 따른다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // {code, message, traceId}. traceId 는 Micrometer Tracing 이 MDC 에 넣은 값 (docs 6-2)
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getMessage(), MDC.get("traceId")));
    }

    @ExceptionHandler(DeliveryAlreadyExistsException.class)
    ProblemDetail handleAlreadyExists(DeliveryAlreadyExistsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // 같은 주문이 동시에 두 번 들어와 existsByOrderId 를 둘 다 통과한 경우 → order_id UNIQUE 위반
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "delivery already exists");
    }
}
