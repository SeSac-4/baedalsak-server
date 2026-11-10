package com.baedalsak.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 비밀번호 재설정 요청 (#162) — 메일로 받은 코드 + 새 비밀번호 */
public record PasswordResetRequest(

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    String email,

    // Cognito 기본 인증 코드 = 숫자 6자리
    @NotBlank(message = "인증 코드는 필수 입력 항목입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 숫자 6자리입니다.")
    String code,

    // 가입과 같은 규칙 (Cognito 비밀번호 정책: 8자 이상 + 소문자 + 숫자)
    @NotBlank(message = "새 비밀번호는 필수 입력 항목입니다.")
    @Size(min = 8, max = 50, message = "비밀번호는 8~50자여야 합니다.")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*\\d).+$", message = "비밀번호에 영문 소문자와 숫자를 모두 넣어 주세요.")
    String newPassword
) {

  // 코드·비밀번호가 로그에 찍히지 않게
  @Override
  public String toString() {
    return "PasswordResetRequest[email=" + email + ", code=****, newPassword=****]";
  }
}