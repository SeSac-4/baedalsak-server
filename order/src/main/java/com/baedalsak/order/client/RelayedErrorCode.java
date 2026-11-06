package com.baedalsak.order.client;

import com.baedalsak.common.error.ErrorCode;

/**
 * 다른 서비스가 준 에러를 같은 코드·메시지로 고객에게 전달한다 (토스 거절 사유가 message 에 담겨 온다, 사가 결정 3-2).
 */
record RelayedErrorCode(String name, int status, String message) implements ErrorCode {

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
