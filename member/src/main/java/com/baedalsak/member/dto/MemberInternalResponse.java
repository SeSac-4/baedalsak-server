package com.baedalsak.member.dto;

import com.baedalsak.common.logging.MaskingUtils;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;

/**
 * [내부] 회원 조회 응답 (GET /internal/members/{memberId}).
 * order(주문 생성 시 고객 phone), delivery(배차 시 라이더 name)가 스냅샷으로 저장한다.
 * 필드 이름은 order 의 MemberInternal 과 같아야 한다 (설계서 '서비스 경계').
 */
public record MemberInternalResponse(
    String name,
    String phone,
    String email,
    Role role
) {

  // 엔티티 → 응답 변환을 한곳에 모아 둔다
  public static MemberInternalResponse from(Member member) {
    return new MemberInternalResponse(member.getName(), member.getPhone(), member.getEmail(), member.getRole());
  }

  // 로그에 휴대폰 번호가 그대로 남지 않게 (N4.4)
  @Override
  public String toString() {
    return "MemberInternalResponse[name=" + name + ", phone=" + MaskingUtils.maskPhone(phone)
        + ", email=" + email + ", role=" + role + "]";
  }
}