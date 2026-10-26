package com.baedalsak.delivery.auth;

/**
 * 요청자. gateway 가 토큰을 검증한 뒤 넣어 준 X-Member-Id 헤더 값이다 (설계서 'API 명세' 탭 공통 규칙).
 * 역할(RIDER) 검증은 gateway 가 하고, 서비스는 요청자 id 로 소유권·배정만 판단한다.
 */
public record LoginMember(Long id) {

    public static final String ID_HEADER = "X-Member-Id";
}
