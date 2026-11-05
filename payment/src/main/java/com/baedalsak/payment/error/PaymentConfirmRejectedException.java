package com.baedalsak.payment.error;

import com.baedalsak.common.error.BusinessException;

/**
 * 토스가 결제 승인을 확정 거절함 (카드 거절·한도 초과 등, #125). 400 PAYMENT_CONFIRM_REJECTED 로 응답하고
 * message 에 토스 메시지를 담아 U-07 '사유'로 보여 준다. 토스 메시지가 없으면 코드의 기본 문구.
 */
public class PaymentConfirmRejectedException extends BusinessException {

    private final String tossMessage;

    public PaymentConfirmRejectedException(String tossMessage) {
        super(PaymentErrorCode.PAYMENT_CONFIRM_REJECTED);
        this.tossMessage = tossMessage;
    }

    // 응답 message: 토스 메시지, 없으면 기본 문구
    public String responseMessage() {
        return tossMessage == null || tossMessage.isBlank() ? getErrorCode().getMessage() : tossMessage;
    }
}
