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

    EMAIL_DUPLICATED(409, "이미 가입된 이메일입니다."),

    // 휴대폰 인증 (#164)
    PHONE_CODE_COOLDOWN(429, "인증번호는 1분 뒤에 다시 받을 수 있습니다."),
    PHONE_CODE_DAILY_LIMIT(429, "오늘은 인증번호를 더 받을 수 없습니다. 내일 다시 시도해 주세요."),
    PHONE_CODE_TRIES_EXCEEDED(429, "인증번호를 여러 번 틀렸습니다. 인증번호를 다시 받아 주세요.");

    private final int status;
    private final String message;
}
