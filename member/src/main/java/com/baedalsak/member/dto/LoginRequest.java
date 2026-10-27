package com.baedalsak.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인 요청 (API 명세 8.2.1 LoginRequest). */
public record LoginRequest(

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    String email,

    // 로그인에서는 비밀번호 규칙(소문자·숫자)을 검사하지 않는다 — 틀리면 그냥 401 (가입에서 검사)
    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Size(max = 50)
    String password
) {

  // 비밀번호가 로그에 찍히지 않게
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=****]";
  }
}
