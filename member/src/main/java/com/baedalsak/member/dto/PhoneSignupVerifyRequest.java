package com.baedalsak.member.dto;

import com.baedalsak.common.logging.MaskingUtils;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 가입 화면: 번호 + 받은 인증번호 확인 (#164) */
public record PhoneSignupVerifyRequest(

    @NotBlank(message = "휴대폰 번호는 필수 입력 항목입니다.")
    @Pattern(regexp = "^010-?\\d{4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
    String phone,

    @NotBlank(message = "인증번호를 입력해 주세요.")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리입니다.")
    String code
) {

  @Override
  public String toString() {
    return "PhoneSignupVerifyRequest[phone=" + MaskingUtils.maskPhone(phone) + ", code=****]";
  }
}
