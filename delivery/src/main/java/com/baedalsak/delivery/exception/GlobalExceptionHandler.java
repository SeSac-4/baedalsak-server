package com.baedalsak.delivery.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * HTTP 에러 응답 (ProblemDetail). @Valid 실패(400)는 Spring 기본 처리를 따른다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
