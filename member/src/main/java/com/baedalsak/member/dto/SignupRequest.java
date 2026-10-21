package com.baedalsak.member.dto;

import com.baedalsak.member.domain.Role;
import com.baedalsak.member.domain.VehicleType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
  
  @NotBlank(message = "이메일은 필수 입력 항목입니다.")
  @Email (message = "이메일 형식이 올바르지 않습니다.")
  @Size (max = 255)
  String email,

  @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
  @Size(min = 8, max = 50, message = "비밀번호는 8~50자여야 합니다.")
  String password,

  @NotBlank(message = "이름은 필수 입력 항목입니다.")
  @Size (max = 50)
  String name,

  @NotBlank(message = "전화번호는 필수 입력 항목입니다.")
  @Pattern (regexp = "^010-?\\d{4}-?\\d{4}$")
  String phone,

  @NotNull (message = "역할은 필수 입력 항목입니다.")
  Role role,

  //가게 오너일때만
  @Size (max = 100)
  String storeName,

  //라이더일 때만
  VehicleType vehicleType

) {

  public String validateByRole() {
    if (role == Role.OWNER && (storeName == null || storeName.isBlank())) {
      return "사장님 가입에는 가게 이름이 필요합니다.";
    }
    if (role == Role.RIDER && vehicleType == null) {
      return  "배달 파트너 가입에는 운송수단이 필요합니다.";
    }
    return null;
  }

}
