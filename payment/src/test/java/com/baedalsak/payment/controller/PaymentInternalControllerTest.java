package com.baedalsak.payment.controller;

import com.baedalsak.common.error.GlobalExceptionHandler;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.request.InternalConfirmPaymentRequest;
import com.baedalsak.payment.dto.response.ExpireResultResponse;
import com.baedalsak.payment.dto.response.PaymentReadyResponse;
import com.baedalsak.payment.dto.response.PaymentStatusResponse;
import com.baedalsak.payment.service.PaymentConfirmService;
import com.baedalsak.payment.service.PaymentInternalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * payment 내부 API (#128). 경로 · 요청 검증(400) · 응답 필드. 로그인 헤더 없이 부른다(서비스 간 호출)
 */
class PaymentInternalControllerTest {

    PaymentInternalService paymentInternalService = mock(PaymentInternalService.class);
    PaymentConfirmService paymentConfirmService = mock(PaymentConfirmService.class);
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentInternalController(paymentInternalService, paymentConfirmService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 결제_요청_생성은_결제창_값을_돌려준다() throws Exception {
        given(paymentInternalService.create(any()))
                .willReturn(new PaymentReadyResponse(7L, "BDS-1005-1", "참치김밥 외 1건", 18000));

        mockMvc.perform(post("/internal/payments").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId":1005,"amount":18000,"customerId":1,"ownerId":10,"storeId":3,"storeName":"싹분식",
                         "customerEmail":"customer@test.com","ownerEmail":"owner@test.com","orderName":"참치김밥 외 1건"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(7))
                .andExpect(jsonPath("$.tossOrderId").value("BDS-1005-1"))
                .andExpect(jsonPath("$.orderName").value("참치김밥 외 1건"))
                .andExpect(jsonPath("$.amount").value(18000));
    }

    @Test
    void 결제_요청_생성에_필수_값이_없으면_400() throws Exception {
        mockMvc.perform(post("/internal/payments").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId":1005,"amount":0,"customerId":1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(paymentInternalService);
    }

    @Test
    void 내부_승인은_요청_값을_그대로_넘긴다() throws Exception {
        mockMvc.perform(post("/internal/payments/confirm").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId":1005,"customerId":1,"paymentKey":"tgen_key","amount":18000}
                        """))
                .andExpect(status().isOk());
        verify(paymentConfirmService).confirmInternal(new InternalConfirmPaymentRequest(1005L, 1L, "tgen_key", 18000));
    }

    @Test
    void 상태_조회는_orderIds_로_받는다() throws Exception {
        given(paymentInternalService.findStatuses(List.of(1005L, 1006L)))
                .willReturn(List.of(new PaymentStatusResponse(1005L, 7L, PaymentStatus.REQUESTED, 18000, null)));

        mockMvc.perform(get("/internal/payments").param("orderIds", "1005,1006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1005))
                .andExpect(jsonPath("$[0].paymentId").value(7))
                .andExpect(jsonPath("$[0].status").value("REQUESTED"));
    }

    @Test
    void 만료는_주문별_결과를_돌려준다() throws Exception {
        given(paymentInternalService.expire(List.of(1005L))).willReturn(List.of(ExpireResultResponse.expired(1005L)));

        mockMvc.perform(post("/internal/payments/expire").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderIds":[1005]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1005))
                .andExpect(jsonPath("$[0].result").value("EXPIRED"));
    }

    @Test
    void 만료에_orderIds_가_비어_있으면_400() throws Exception {
        mockMvc.perform(post("/internal/payments/expire").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderIds":[]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(paymentInternalService);
    }
}
