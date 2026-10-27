package com.baedalsak.payment.error;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * payment 에서 던진 결제 코드·공통 코드가 common 의 GlobalExceptionHandler 를 거쳐
 * {code, message, errors?, traceId} 로 응답되는지 확인
 */
class PaymentErrorResponseTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 결제_코드는_결제_에러_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/payment-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 결제_승인_실패는_502와_설계서_문구() throws Exception {
        mockMvc.perform(get("/test/gateway-error"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"))
                .andExpect(jsonPath("$.message").value("결제 확인 중 오류. 다시 시도해 주세요"));
    }

    @Test
    void 공통_코드는_공통_에러_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/dependency"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void 본문_검증_실패는_400과_필드별_사유() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("paymentKey"));
    }

    @Test
    void 경로_파라미터_타입이_틀리면_400() throws Exception {
        mockMvc.perform(get("/test/payments/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 지원하지_않는_메서드는_405() throws Exception {
        mockMvc.perform(post("/test/payment-not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 처리하지_못한_예외는_500() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void MDC의_traceId를_응답에_담는다() throws Exception {
        MDC.put("traceId", "trace-123");
        try {
            mockMvc.perform(get("/test/payment-not-found"))
                    .andExpect(jsonPath("$.traceId").value("trace-123"));
        } finally {
            MDC.remove("traceId");
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/test/payment-not-found")
        void paymentNotFound() {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        @GetMapping("/test/gateway-error")
        void gatewayError() {
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
        }

        @GetMapping("/test/dependency")
        void dependency() {
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE);
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("예상하지 못한 오류");
        }

        @GetMapping("/test/payments/{paymentId}")
        void payment(@PathVariable Long paymentId) {
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody TestRequest request) {
        }
    }

    record TestRequest(@NotBlank String paymentKey) {
    }
}
