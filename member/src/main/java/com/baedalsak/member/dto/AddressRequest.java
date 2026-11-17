package com.baedalsak.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 배달 주소 추가 · 수정 (#215), 고객 가입의 address 칸도 같은 모양.
 * 주소 · 동은 우편번호 검색 결과, 좌표는 카카오맵 지오코딩 결과를 프론트가 보낸다.
 */
public record AddressRequest(

    @Size(max = 20, message = "별칭은 20자 이하로 입력해 주세요.")
    String label,

    @NotBlank(message = "주소를 입력해 주세요.")
    @Size(max = 200, message = "주소는 200자 이하로 입력해 주세요.")
    String address,

    @Size(max = 50, message = "상세 주소는 50자 이하로 입력해 주세요.")
    String addressDetail,

    @NotBlank(message = "동을 입력해 주세요.")
    @Size(max = 50, message = "동은 50자 이하로 입력해 주세요.")
    String dong,

    // 한국 범위 (위도 33~39, 경도 124~132) — 0,0 같은 잘못된 좌표로 가게 목록이 비는 것 방지
    @NotNull(message = "주소 좌표가 필요합니다.")
    @DecimalMin(value = "33.0", message = "주소 좌표가 올바르지 않습니다.")
    @DecimalMax(value = "39.0", message = "주소 좌표가 올바르지 않습니다.")
    Double lat,

    @NotNull(message = "주소 좌표가 필요합니다.")
    @DecimalMin(value = "124.0", message = "주소 좌표가 올바르지 않습니다.")
    @DecimalMax(value = "132.0", message = "주소 좌표가 올바르지 않습니다.")
    Double lng,

    // true 면 이 주소를 기본 주소로 (선택). 첫 주소는 이 값과 상관없이 기본
    @JsonProperty("isDefault")
    Boolean isDefault

) {

  // 주소는 개인정보 → 로그에는 별칭 · 동만
  @Override
  public String toString() {
    return "AddressRequest[label=" + label + ", dong=" + dong + ", address=****, isDefault=" + isDefault + "]";
  }
}
