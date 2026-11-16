package com.baedalsak.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 가입 화면: 이메일 + 받은 인증번호 확인 (#207) */
public record EmailSignupVerifyRequest(

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    String email,

    @NotBlank(message = "인증번호를 입력해 주세요.")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리입니다.")
    String code
) {

  @Override
  public String toString() {
    return "EmailSignupVerifyRequest[email=" + email + ", code=****]";
  }
}
