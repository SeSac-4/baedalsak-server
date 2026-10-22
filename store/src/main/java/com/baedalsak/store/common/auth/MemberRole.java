package com.baedalsak.store.common.auth;

/**
 * 회원 역할. gateway 가 X-Member-Role 헤더로 전달한다.
 */
public enum MemberRole {
    CUSTOMER,
    OWNER,
    RIDER
}
