package com.baedalsak.member.error;

import com.baedalsak.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * member 도메인 에러 코드 (설계서 'API 명세' 에러표). 공통 코드는 CommonErrorCode 를 쓴다.
 * 회원 없음은 세부 코드 없이 CommonErrorCode.NOT_FOUND 를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    EMAIL_DUPLICATED(409, "이미 가입된 이메일입니다.");

    private final int status;
    private final String message;
}
