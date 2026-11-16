package com.baedalsak.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 가입 화면: 이 이메일로 인증번호를 보내 달라 (#207) */
public record EmailCodeRequest(

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    String email
) {
}
