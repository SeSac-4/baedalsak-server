package com.baedalsak.store.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.store.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공통 모듈(common)의 에러 응답·요청자 식별이 store 에 적용되는지 (#55).
 * 에러 본문은 {code, message, errors?, traceId} (설계서 API 명세 57행)
 */
class CommonErrorResponseTest extends IntegrationTest {

    @Test
    void 없는_경로는_404_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/v1/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 허용되지_않는_메서드는_405_METHOD_NOT_ALLOWED() throws Exception {
        mockMvc.perform(post("/v1/categories"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 경로_파라미터_타입이_틀리면_400_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(get("/v1/stores/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("storeId"));
    }

    @Test
    void 요청자_헤더가_없거나_잘못되면_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/v1/stores/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/v1/stores/me")
                        .header(LoginMember.ID_HEADER, "abc")
                        .header(LoginMember.ROLE_HEADER, "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 본문_검증_실패는_필드별_사유를_담는다() throws Exception {
        mockMvc.perform(post("/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(LoginMember.ID_HEADER, "10")
                        .header(LoginMember.ROLE_HEADER, "OWNER")
                        .content("{\"name\":\"\",\"category\":\"SNACK\",\"address\":\"주소\",\"minOrderAmount\":0,\"deliveryFee\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].reason").value("가게 이름을 입력해 주세요."));
    }
}
