package com.baedalsak.payment.error;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 확정 실패(#125)는 payment 전용 핸들러가 공통 핸들러보다 먼저 처리해 message 에 토스 메시지를 담는다.
 * 그 외 업무 예외는 공통 핸들러 그대로
 */
class PaymentExceptionHandlerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 공통 핸들러를 먼저 등록해도 @Order 로 payment 핸들러가 먼저 처리하는지 확인
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(), new PaymentExceptionHandler())
                .build();
    }

    @Test
    void 확정_실패는_400과_토스_메시지() throws Exception {
        mockMvc.perform(get("/test/rejected"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_CONFIRM_REJECTED"))
                .andExpect(jsonPath("$.message").value("카드사에서 승인을 거절했습니다."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 토스_메시지가_없으면_기본_문구() throws Exception {
        mockMvc.perform(get("/test/rejected-without-message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_CONFIRM_REJECTED"))
                .andExpect(jsonPath("$.message").value(PaymentErrorCode.PAYMENT_CONFIRM_REJECTED.getMessage()));
    }

    @Test
    void 다른_업무_예외는_공통_핸들러_문구() throws Exception {
        mockMvc.perform(get("/test/key-mismatch"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_KEY_MISMATCH"))
                .andExpect(jsonPath("$.message").value(PaymentErrorCode.PAYMENT_KEY_MISMATCH.getMessage()));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/rejected")
        void rejected() {
            throw new PaymentConfirmRejectedException("카드사에서 승인을 거절했습니다.");
        }

        @GetMapping("/test/rejected-without-message")
        void rejectedWithoutMessage() {
            throw new PaymentConfirmRejectedException(null);
        }

        @GetMapping("/test/key-mismatch")
        void keyMismatch() {
            throw new BusinessException(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
        }
    }
}
