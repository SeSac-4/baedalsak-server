package com.baedalsak.member.dto;

import java.util.List;

import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.common.logging.MaskingUtils;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.domain.VehicleType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

    @NotBlank(message = "이메일은 필수 입력 항목입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 255) String email,

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.") @Size(min = 8, max = 50, message = "비밀번호는 8~50자여야 합니다.") String password,

    @NotBlank(message = "이름은 필수 입력 항목입니다.") @Size(max = 50) String name,

    @NotBlank(message = "전화번호는 필수 입력 항목입니다.") @Pattern(regexp = "^010-?\\d{4}-?\\d{4}$") String phone,

    @NotNull(message = "역할은 필수 입력 항목입니다.") Role role,

    // 가게 오너일때만
    @Size(max = 100) String storeName,

    // 라이더일 때만
    VehicleType vehicleType

) {

  public List<FieldErrorDetail> validateByRole() {
    if (role == Role.OWNER && (storeName == null || storeName.isBlank())) {
      return List.of(new FieldErrorDetail("storeName", "사장님 가입에는 가게 이름이 필요합니다."));
    }
    if (role == Role.RIDER && vehicleType == null) {
      return List.of(new FieldErrorDetail("vehicleType", "라이더 가입에는 운송수단이 필요합니다."));
    }
    return List.of();
  }
    // record 기본 toString은 password까지 그대로 출력함 → 로그 유출 방지
  @Override
  public String toString() {
    return "SignupRequest[email=" + email
        + ", password=****"
        + ", name=" + name
        + ", phone=" + MaskingUtils.maskPhone(phone)
        + ", role=" + role
        + ", storeName=" + storeName
        + ", vehicleType=" + vehicleType + "]";
  }

}
