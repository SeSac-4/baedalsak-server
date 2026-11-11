package com.baedalsak.member.dto;

import com.baedalsak.common.logging.MaskingUtils;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 내 계정 정보 (GET /v1/members/me, 설계서 API 명세 8.3.1 MemberMe).
 * 사장님은 storeNameAtSignup(가게 등록 화면 기본값), 라이더는 vehicleType 이 추가로 채워진다.
 * 사장님의 실제 가게 정보는 프론트가 store GET /stores/me 로 따로 조회한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberMeResponse(
    Long id,
    String email,
    String name,
    String phone,
    Role role,
    boolean phoneVerified,      // 휴대폰 인증 여부 (#164) — 배달 알림 문자를 받을 수 있는지
    String storeNameAtSignup,   // 사장님만
    String vehicleType          // 라이더만
) {

  public static MemberMeResponse of(Member member, String storeNameAtSignup, String vehicleType) {
    return new MemberMeResponse(member.getId(), member.getEmail(), member.getName(), member.getPhone(),
        member.getRole(), member.isPhoneVerified(), storeNameAtSignup, vehicleType);
  }

  @Override
  public String toString() {
    return "MemberMeResponse[id=" + id + ", email=" + email + ", name=" + name
        + ", phone=" + MaskingUtils.maskPhone(phone) + ", role=" + role + ", phoneVerified=" + phoneVerified
        + ", storeNameAtSignup=" + storeNameAtSignup + ", vehicleType=" + vehicleType + "]";
  }
}