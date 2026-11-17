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

    PHONE_ALREADY_VERIFIED(409, "이미 인증된 휴대폰 번호입니다."),

    EMAIL_DUPLICATED(409, "이미 가입된 이메일입니다."),

    // 회원 탈퇴 (#188). 사장님·라이더는 가게·배달 확인 API 가 생기기 전까지 막음, seed(id < 1000)는 팀 공용 계정 보호
    WITHDRAWAL_NOT_ALLOWED(403, "사장님·라이더와 테스트 계정은 앱에서 탈퇴할 수 없습니다. 고객센터로 문의해 주세요."),

    // 휴대폰 인증 (#164)
    PHONE_CODE_COOLDOWN(429, "인증번호는 1분 뒤에 다시 받을 수 있습니다."),
    PHONE_CODE_DAILY_LIMIT(429, "오늘은 인증번호를 더 받을 수 없습니다. 내일 다시 시도해 주세요."),
    PHONE_CODE_TRIES_EXCEEDED(429, "인증번호를 여러 번 틀렸습니다. 인증번호를 다시 받아 주세요."),

    // 전화번호 변경 (#208). 휴대폰 인증 필수(sms.verification-required=true)일 때 인증 없이 바꾸려 하면
    PHONE_CHANGE_VERIFICATION_REQUIRED(403, "새 번호로 인증번호를 받아 확인해야 번호를 바꿀 수 있습니다."),

    // 가입 전 이메일 인증 (#207) — 휴대폰과 같은 규칙
    EMAIL_CODE_COOLDOWN(429, "인증번호는 1분 뒤에 다시 받을 수 있습니다."),
    EMAIL_CODE_DAILY_LIMIT(429, "오늘은 이 이메일로 인증번호를 더 받을 수 없습니다. 내일 다시 시도해 주세요."),
    EMAIL_CODE_TRIES_EXCEEDED(429, "인증번호를 여러 번 틀렸습니다. 인증번호를 다시 받아 주세요.");

    private final int status;
    private final String message;
}
