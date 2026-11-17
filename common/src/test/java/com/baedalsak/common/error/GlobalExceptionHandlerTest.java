package com.baedalsak.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 공통 에러 처리 (#186): 클라이언트 실수가 500 으로 바뀌지 않는지. 서버 없이 가짜 컨트롤러 하나로 확인 */
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @RestController
  static class EchoController {
    record Body(String name) {}

    @PostMapping("/echo")
    Body echo(@RequestBody Body body) {
      return body;
    }

    @GetMapping("/forbidden/default")
    void forbiddenDefault() {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    @GetMapping("/forbidden/custom")
    void forbiddenCustom() {
      throw new BusinessException(CommonErrorCode.FORBIDDEN, "본인에게 배정된 배달만 픽업할 수 있습니다.");
    }
  }

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void JSON_이_아닌_Content_Type_은_500_이_아니라_400() throws Exception {
    mockMvc.perform(post("/echo").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("Content-Type"));
  }

  @Test
  void JSON_으로_보내면_그대로_처리() throws Exception {
    mockMvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"싹\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("싹"));
  }

  @Test
  void businessExceptionUsesCodeMessageByDefault() throws Exception {
    mockMvc.perform(get("/forbidden/default"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value(CommonErrorCode.FORBIDDEN.getMessage()));
  }

  @Test
  void businessExceptionKeepsCodeWithCustomMessage() throws Exception {
    mockMvc.perform(get("/forbidden/custom"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("본인에게 배정된 배달만 픽업할 수 있습니다."));
  }
}
