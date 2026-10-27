package com.baedalsak.member.dto;

import com.baedalsak.member.domain.Role;

/**
 * 로그인·가입 응답 본문. 토큰은 쿠키로만 주고 본문에는 넣지 않는다.
 * 프론트는 role 로 역할별 홈(고객 U-01 / 사장님 O-01 / 라이더 R-01)으로 이동한다.
 */
public record LoginResponse(
    Long memberId,
    Role role
) {
}
