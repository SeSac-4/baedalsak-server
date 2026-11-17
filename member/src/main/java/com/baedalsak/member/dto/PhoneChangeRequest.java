package com.baedalsak.member.dto;

import com.baedalsak.common.logging.MaskingUtils;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 내 전화번호 변경 (#208) — 인증번호 받기 · 스위치 꺼짐 때 바로 변경에 쓰는 새 번호 (형식은 가입과 같음) */
public record PhoneChangeRequest(

    @NotBlank(message = "휴대폰 번호는 필수 입력 항목입니다.")
    @Pattern(regexp = "^010-?\\d{4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
    String phone
) {

  @Override
  public String toString() {
    return "PhoneChangeRequest[phone=" + MaskingUtils.maskPhone(phone) + "]";
  }
}
