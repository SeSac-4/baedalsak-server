package com.baedalsak.order.client;

import com.baedalsak.common.logging.MaskingUtils;

/**
 * member [내부] 회원 조회 응답 (GET /internal/members/{memberId}). 설계서 '서비스 경계' 탭 MemberInternal.
 * 주문 생성 때 고객 phone·email, 사장님 email 을 스냅샷한다.
 */
public record MemberInternal(
        String name,
        String phone,
        String email,
        String role
) {

    // 로그에 휴대폰 번호가 그대로 남지 않게 한다 (N4.4)
    @Override
    public String toString() {
        return "MemberInternal[name=" + name + ", phone=" + MaskingUtils.maskPhone(phone)
                + ", email=" + email + ", role=" + role + "]";
    }
}
