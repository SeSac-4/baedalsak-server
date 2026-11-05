package com.baedalsak.payment.error;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.baedalsak.common.error.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * payment 전용 예외 응답 (#125). 공통 GlobalExceptionHandler 는 message 를 에러 코드 문구로만 만들기 때문에,
 * 토스 메시지를 전달해야 하는 확정 실패만 여기서 먼저 처리한다. 응답 형식 {code, message, traceId} 는 공통과 같다.
 * 공통 코드는 수정하지 않는다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentConfirmRejectedException.class)
    public ResponseEntity<ErrorResponse> handleConfirmRejected(PaymentConfirmRejectedException e) {
        PaymentErrorCode code = PaymentErrorCode.PAYMENT_CONFIRM_REJECTED;
        // 공통 핸들러와 같은 4xx WARN 형식
        log.warn("업무 예외 code={} status={} fields={}", code.name(), code.getStatus(), "[]");
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(code.name(), e.responseMessage(), null, MDC.get("traceId")));
    }
}
