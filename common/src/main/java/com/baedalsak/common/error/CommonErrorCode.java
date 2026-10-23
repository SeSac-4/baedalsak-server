package com.baedalsak.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 서비스가 같이 쓰는 공통 에러 코드 (설계서 'API 명세' 에러표).
 * 도메인 에러 코드는 여기에 넣지 않고 각 서비스의 ErrorCode 구현 enum 에 둔다 (2026-10-23 팀 결정).
 * 404 는 세부 코드가 있는 리소스면 그 서비스의 세부 코드를, 그 외(회원·장바구니 항목 등)는 NOT_FOUND 를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_FAILED(400, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    NOT_FOUND(404, "대상을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청입니다."),
    TOO_MANY_REQUESTS(429, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다."),
    DEPENDENCY_UNAVAILABLE(503, "일시적으로 서비스를 이용할 수 없습니다.");

    private final int status;
    private final String message;
}
