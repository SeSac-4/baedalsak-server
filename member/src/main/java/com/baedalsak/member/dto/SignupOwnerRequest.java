package com.baedalsak.member.dto;

import com.baedalsak.common.logging.MaskingUtils;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 사장님 가입 (API 명세 8.1.1) */
public record SignupOwnerRequest(

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    String email,

    // Cognito 비밀번호 정책과 같게: 8자 이상 + 소문자 + 숫자 (대문자·특수문자는 선택)
    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Size(min = 8, max = 50, message = "비밀번호는 8~50자여야 합니다.")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*\\d).+$", message = "비밀번호에 영문 소문자와 숫자를 모두 넣어 주세요.")
    String password,

    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    @Size(max = 50)
    String name,

    @NotBlank(message = "휴대폰 번호는 필수 입력 항목입니다.")
    @Pattern(regexp = "^010-?\\d{4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
    String phone,

    // owner_profile.store_name_at_signup 에 저장. 가게 등록(store)은 가입 후 따로
    @NotBlank(message = "가게 이름은 필수 입력 항목입니다.")
    @Size(max = 100)
    String storeName

) implements SignupRequest {

  // record 기본 toString 은 비밀번호까지 찍는다 → 로그 유출 방지
  @Override
  public String toString() {
    return "SignupOwnerRequest[email=" + email + ", password=****, name=" + name
        + ", phone=" + MaskingUtils.maskPhone(phone) + ", storeNme=" + storeName + "]";
  }
}