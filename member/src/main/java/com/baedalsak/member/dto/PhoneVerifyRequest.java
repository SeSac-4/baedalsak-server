package com.baedalsak.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 내 휴대폰 번호 인증 확인 (#164) — 번호는 받지 않음 (DB 의 내 번호) */
public record PhoneVerifyRequest(

    @NotBlank(message = "인증번호를 입력해 주세요.")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리입니다.")
    String code
) {

  @Override
  public String toString() {
    return "PhoneVerifyRequest[code=****]";
  }
}