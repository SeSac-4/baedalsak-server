package com.baedalsak.store.error;

import com.baedalsak.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * store 도메인 에러 코드 (설계서 'API 명세' 에러표). 공통 코드는 CommonErrorCode 를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements ErrorCode {

    STORE_NOT_FOUND(404, "가게를 찾을 수 없습니다."),
    MENU_NOT_FOUND(404, "메뉴를 찾을 수 없습니다."),
    STORE_ALREADY_EXISTS(409, "이미 등록한 가게가 있습니다. 사장님은 가게 1개만 운영할 수 있습니다.");

    private final int status;
    private final String message;
}
