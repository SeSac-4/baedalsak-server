package com.baedalsak.delivery.error;

import com.baedalsak.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * delivery 도메인 에러 코드 (설계서 'API 명세' 에러표). 공통 코드(VALIDATION_FAILED · UNAUTHORIZED · FORBIDDEN 등)는 CommonErrorCode 를 쓴다.
 * 프론트는 code 로 분기한다 (예: R-01 에서 DELIVERY_ALREADY_ASSIGNED → 토스트 + 목록 새로고침).
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ErrorCode {

    DELIVERY_NOT_FOUND(404, "배달 정보를 찾을 수 없습니다."),
    DELIVERY_ALREADY_ASSIGNED(409, "이미 배차된 주문입니다."),
    DELIVERY_INVALID_STATUS(409, "현재 배달 상태에서는 처리할 수 없습니다.");

    private final int status;
    private final String message;
}
