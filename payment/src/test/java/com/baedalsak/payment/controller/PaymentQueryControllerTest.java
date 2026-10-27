package com.baedalsak.payment.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.LoginMemberArgumentResolver;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.GlobalExceptionHandler;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.response.PageResponse;
import com.baedalsak.payment.dto.response.PaymentDetailResponse;
import com.baedalsak.payment.service.PaymentConfirmService;
import com.baedalsak.payment.service.PaymentQueryService;
import com.baedalsak.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 조회 API (#15). 경로·page·size 범위 검증(400)·헤더 없음(401)을 common 핸들러와 함께 확인
 */
class PaymentQueryControllerTest {

    static final LoginMember CUSTOMER = new LoginMember(1L, MemberRole.CUSTOMER);

    PaymentQueryService paymentQueryService = mock(PaymentQueryService.class);
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaymentController controller = new PaymentController(mock(PaymentService.class),
                mock(PaymentConfirmService.class), paymentQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 목록은_기본_page_0_size_20() throws Exception {
        given(paymentQueryService.getMyPayments(any(), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/v1/payments").header("X-Member-Id", "1").header("X-Member-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        verify(paymentQueryService).getMyPayments(CUSTOMER, 0, 20);
    }

    @Test
    void size_가_100을_넘으면_400() throws Exception {
        mockMvc.perform(get("/v1/payments").param("size", "101")
                        .header("X-Member-Id", "1").header("X-Member-Role", "CUSTOMER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(paymentQueryService);
    }

    @Test
    void page_가_음수면_400() throws Exception {
        mockMvc.perform(get("/v1/payments").param("page", "-1")
                        .header("X-Member-Id", "1").header("X-Member-Role", "CUSTOMER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 주문_ID로_결제_상세() throws Exception {
        given(paymentQueryService.getPaymentByOrder(CUSTOMER, 1005L)).willReturn(
                new PaymentDetailResponse(7L, 1005L, PaymentStatus.REQUESTED, 18000, null, null, null));

        mockMvc.perform(get("/v1/payments/by-order/1005")
                        .header("X-Member-Id", "1").header("X-Member-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(7))
                .andExpect(jsonPath("$.orderId").value(1005));
    }

    @Test
    void 결제_ID로_결제_상세() throws Exception {
        given(paymentQueryService.getPayment(CUSTOMER, 7L)).willReturn(
                new PaymentDetailResponse(7L, 1005L, PaymentStatus.REQUESTED, 18000, null, null, null));

        mockMvc.perform(get("/v1/payments/7").header("X-Member-Id", "1").header("X-Member-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(7));
    }

    @Test
    void 요청자_헤더가_없으면_401() throws Exception {
        mockMvc.perform(get("/v1/payments/7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(paymentQueryService);
    }
}
