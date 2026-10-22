package com.baedalsak.store.common.auth;

/**
 * 요청자. gateway 가 토큰을 검증한 뒤 넣어 준 X-Member-Id, X-Member-Role 헤더 값이다 (설계서 'API 명세' 탭 공통 규칙).
 * 역할 검증은 gateway 가 하고, 서비스는 소유권(내 가게)만 검증한다 (N4.2).
 */
public record LoginMember(Long id, MemberRole role) {

    public static final String ID_HEADER = "X-Member-Id";
    public static final String ROLE_HEADER = "X-Member-Role";
}
