package com.baedalsak.store.common;

import com.baedalsak.store.common.auth.CurrentMember;
import com.baedalsak.store.common.auth.LoginMember;
import com.baedalsak.store.common.auth.LoginMemberArgumentResolver;
import com.baedalsak.store.common.error.BusinessException;
import com.baedalsak.store.common.error.ErrorCode;
import com.baedalsak.store.common.error.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청자 헤더 해석과 공통 에러 포맷 {code, message, errors[], traceId} 확인
 */
class CommonWebTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 헤더가_있으면_요청자를_넣어_준다() throws Exception {
        mockMvc.perform(get("/test/me")
                        .header(LoginMember.ID_HEADER, "7")
                        .header(LoginMember.ROLE_HEADER, "OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void 헤더가_없으면_401() throws Exception {
        mockMvc.perform(get("/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @Test
    void 헤더_값이_잘못되면_401() throws Exception {
        mockMvc.perform(get("/test/me")
                        .header(LoginMember.ID_HEADER, "abc")
                        .header(LoginMember.ROLE_HEADER, "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비즈니스_예외는_에러_코드의_상태로_응답한다() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 본문_검증_실패는_필드별_사유를_담는다() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void 지원하지_않는_메서드는_405() throws Exception {
        mockMvc.perform(post("/test/me"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/me")
        LoginMember me(@CurrentMember LoginMember member) {
            return member;
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody TestRequest request) {
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
