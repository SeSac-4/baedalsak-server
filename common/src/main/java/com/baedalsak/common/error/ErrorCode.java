package com.baedalsak.common.error;

/**
 * 에러 코드. 응답 본문 {code, message, errors?, traceId}의 code 는 name(), message 는 getMessage() (설계서 API 명세 57행).
 * 공통 코드는 {@link CommonErrorCode}, 도메인 코드(STORE_NOT_FOUND, CART_EMPTY 등)는 각 서비스가 이 인터페이스를 구현한 enum 으로 둔다.
 */
public interface ErrorCode {

    // enum 이면 상수 이름이 그대로 응답 code 가 된다
    String name();

    int getStatus();

    String getMessage();
}
