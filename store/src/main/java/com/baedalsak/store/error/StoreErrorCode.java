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
    STORE_ALREADY_EXISTS(409, "이미 등록한 가게가 있습니다. 사장님은 가게 1개만 운영할 수 있습니다."),
    STORE_HAS_ACTIVE_ORDERS(409, "진행 중인 주문이 있어 폐업할 수 없습니다. 주문을 모두 처리한 뒤 다시 시도해 주세요.");

    private final int status;
    private final String message;
}
