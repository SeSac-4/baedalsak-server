package com.baedalsak.member.dto;

import com.baedalsak.common.logging.MaskingUtils;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 가입 화면: 이 번호로 인증번호를 보내 달라 (#164) */
public record PhoneCodeRequest(

    @NotBlank(message = "휴대폰 번호는 필수 입력 항목입니다.")
    @Pattern(regexp = "^010-?\\d{4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
    String phone
) {

  @Override
  public String toString() {
    return "PhoneCodeRequest[phone=" + MaskingUtils.maskPhone(phone) + "]";
  }
}
